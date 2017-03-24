package piuk.blockchain.android.ui.contacts.detail;

import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import info.blockchain.wallet.contacts.data.Contact;
import info.blockchain.wallet.contacts.data.FacilitatedTransaction;
import info.blockchain.wallet.contacts.data.PaymentRequest;
import info.blockchain.wallet.payload.PayloadManager;
import info.blockchain.wallet.payload.data.Account;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import piuk.blockchain.android.R;
import piuk.blockchain.android.data.contacts.ContactsPredicates;
import piuk.blockchain.android.data.contacts.FctxDateComparator;
import piuk.blockchain.android.data.datamanagers.ContactsDataManager;
import piuk.blockchain.android.data.notifications.NotificationPayload;
import piuk.blockchain.android.data.rxjava.RxBus;
import piuk.blockchain.android.data.rxjava.RxUtil;
import piuk.blockchain.android.injection.Injector;
import piuk.blockchain.android.ui.base.BaseViewModel;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.util.PrefsUtil;

import static piuk.blockchain.android.ui.contacts.list.ContactsListActivity.KEY_BUNDLE_CONTACT_ID;


@SuppressWarnings("WeakerAccess")
public class ContactDetailViewModel extends BaseViewModel {

    private static final String TAG = ContactDetailViewModel.class.getSimpleName();

    private DataListener dataListener;
    private Observable<NotificationPayload> notificationObservable;
    @VisibleForTesting Contact contact;
    @Inject ContactsDataManager contactsDataManager;
    @Inject PayloadManager payloadManager;
    @Inject PrefsUtil prefsUtil;
    @Inject RxBus rxBus;

    interface DataListener {

        Bundle getPageBundle();

        void updateContactName(String name);

        void finishPage();

        void showRenameDialog(String name);

        void showToast(@StringRes int message, @ToastCustom.ToastType String toastType);

        void showProgressDialog();

        void dismissProgressDialog();

        void showDeleteUserDialog();

        void onTransactionsUpdated(List<FacilitatedTransaction> transactions, String contactName);

        void showAccountChoiceDialog(List<String> accounts, String fctxId);

        void initiatePayment(String uri, String recipientId, String mdid, String fctxId, int defaultIndex);

        void showWaitingForPaymentDialog();

        void showWaitingForAddressDialog();

        void showTransactionDetail(String txHash);

        void showSendAddressDialog(String fctxId);

        void showTransactionDeclineDialog(String fctxId);

        void showTransactionCancelDialog(String fctxId);

    }

    ContactDetailViewModel(DataListener dataListener) {
        Injector.getInstance().getDataManagerComponent().inject(this);
        this.dataListener = dataListener;
    }

    @Override
    public void onViewReady() {
        subscribeToNotifications();
        setupViewModel();
    }

    private void setupViewModel() {
        Bundle bundle = dataListener.getPageBundle();
        if (bundle != null && bundle.getString(KEY_BUNDLE_CONTACT_ID) != null) {
            String id = bundle.getString(KEY_BUNDLE_CONTACT_ID);

            compositeDisposable.add(
                    // Get contacts list
                    contactsDataManager.getContactList()
                            // Find current contact
                            .filter(ContactsPredicates.filterById(id))
                            // Update UI
                            .doOnNext(contact -> {
                                this.contact = contact;
                                dataListener.updateContactName(contact.getName());
                                sortAndUpdateTransactions(contact.getFacilitatedTransactions().values());
                            })
                            // Contact not found, quit page
                            .doOnError(throwable -> showErrorAndQuitPage())
                            // Update contacts in case of new FacilitatedTransactions
                            .flatMapCompletable(contact -> contactsDataManager.fetchContacts())
                            .subscribe(
                                    // Update with FacilitatedTransactions, UI handles diff
                                    () -> sortAndUpdateTransactions(contact.getFacilitatedTransactions().values()),
                                    // Show error if updating contacts failed
                                    throwable -> dataListener.showToast(R.string.contacts_digesting_messages_failed, ToastCustom.TYPE_ERROR)));
        } else {
            showErrorAndQuitPage();
        }
    }

    PrefsUtil getPrefsUtil() {
        return prefsUtil;
    }

    void onDeleteContactClicked() {
        dataListener.showDeleteUserDialog();
    }

    void onDeleteContactConfirmed() {
        dataListener.showProgressDialog();
        compositeDisposable.add(
                contactsDataManager.removeContact(contact)
                        .doAfterTerminate(() -> dataListener.dismissProgressDialog())
                        .subscribe(() -> {
                            // Quit page, show toast
                            dataListener.showToast(R.string.contacts_delete_contact_success, ToastCustom.TYPE_OK);
                            dataListener.finishPage();
                        }, throwable -> dataListener.showToast(R.string.contacts_delete_contact_failed, ToastCustom.TYPE_ERROR)));
    }

    void onRenameContactClicked() {
        dataListener.showRenameDialog(contact.getName());
    }

    void onContactRenamed(String name) {
        //noinspection StatementWithEmptyBody
        if (name.equals(contact.getName())) {
            // No problem here
        } else if (name.isEmpty()) {
            dataListener.showToast(R.string.contacts_rename_invalid_name, ToastCustom.TYPE_ERROR);
        } else {
            dataListener.showProgressDialog();

            compositeDisposable.add(
                    contactsDataManager.renameContact(contact.getId(), name)
                            .doAfterTerminate(() -> dataListener.dismissProgressDialog())
                            .subscribe(
                                    () -> {
                                        dataListener.updateContactName(name);
                                        dataListener.showToast(R.string.contacts_rename_success, ToastCustom.TYPE_OK);
                                    },
                                    throwable -> dataListener.showToast(R.string.contacts_rename_failed, ToastCustom.TYPE_ERROR)));
        }
    }

    void onTransactionClicked(String fctxId) {
        FacilitatedTransaction transaction = contact.getFacilitatedTransactions().get(fctxId);

        if (transaction == null) {
            dataListener.showToast(R.string.contacts_transaction_not_found_error, ToastCustom.TYPE_ERROR);
        } else {

            // Payment request sent, waiting for address from recipient
            if (transaction.getState().equals(FacilitatedTransaction.STATE_WAITING_FOR_ADDRESS)
                    && transaction.getRole().equals(FacilitatedTransaction.ROLE_RPR_INITIATOR)) {

                dataListener.showWaitingForAddressDialog();

                // Payment request sent, waiting for payment
            } else if (transaction.getState().equals(FacilitatedTransaction.STATE_WAITING_FOR_PAYMENT)
                    && transaction.getRole().equals(FacilitatedTransaction.ROLE_PR_INITIATOR)) {

                dataListener.showWaitingForPaymentDialog();

                // Received payment request, need to send address to sender
            } else if (transaction.getState().equals(FacilitatedTransaction.STATE_WAITING_FOR_ADDRESS)
                    && transaction.getRole().equals(FacilitatedTransaction.ROLE_PR_RECEIVER)) {

                List<String> accountNames = new ArrayList<>();
                //noinspection Convert2streamapi
                for (Account account : payloadManager.getPayload().getHdWallets().get(0).getAccounts()) {
                    if (!account.isArchived()) {
                        accountNames.add(account.getLabel());
                    }
                }

                if (accountNames.size() == 1) {
                    // Only one account, ask if you want to send an address
                    dataListener.showSendAddressDialog(fctxId);
                } else {
                    // Show dialog allowing user to select which account they want to use
                    dataListener.showAccountChoiceDialog(accountNames, fctxId);
                }

                // Waiting for payment
            } else if (transaction.getState().equals(FacilitatedTransaction.STATE_WAITING_FOR_PAYMENT)
                    && transaction.getRole().equals(FacilitatedTransaction.ROLE_RPR_RECEIVER)) {

                dataListener.initiatePayment(
                        transaction.toBitcoinURI(),
                        contact.getId(),
                        contact.getMdid(),
                        transaction.getId(),
                        payloadManager.getPayload().getHdWallets().get(0).getDefaultAccountIdx());

                // Payment sent, show detail regardless of role
            } else if (transaction.getState().equals(FacilitatedTransaction.STATE_PAYMENT_BROADCASTED)) {

                dataListener.showTransactionDetail(transaction.getTxHash());
            }
        }
    }

    void onTransactionLongClicked(String fctxId) {
        compositeDisposable.add(
                contactsDataManager.getFacilitatedTransactions()
                        .filter(contactTransactionModel -> contactTransactionModel.getFacilitatedTransaction().getId().equals(fctxId))
                        .subscribe(contactTransactionModel -> {
                            FacilitatedTransaction fctx = contactTransactionModel.getFacilitatedTransaction();

                            if (fctx.getState().equals(FacilitatedTransaction.STATE_WAITING_FOR_ADDRESS)) {
                                if (fctx.getRole().equals(FacilitatedTransaction.ROLE_PR_RECEIVER)) {
                                    dataListener.showTransactionDeclineDialog(fctxId);
                                } else if (fctx.getRole().equals(FacilitatedTransaction.ROLE_RPR_INITIATOR)) {
                                    dataListener.showTransactionCancelDialog(fctxId);
                                }

                            } else if (fctx.getState().equals(FacilitatedTransaction.STATE_WAITING_FOR_PAYMENT)) {
                                if (fctx.getRole().equals(FacilitatedTransaction.ROLE_RPR_RECEIVER)) {
                                    dataListener.showTransactionDeclineDialog(fctxId);
                                } else if (fctx.getRole().equals(FacilitatedTransaction.ROLE_PR_INITIATOR)) {
                                    dataListener.showTransactionCancelDialog(fctxId);
                                }
                            }
                        }, throwable -> showErrorAndQuitPage()));
    }

    void confirmDeclineTransaction(String fctxId) {
        compositeDisposable.add(
                contactsDataManager.getContactFromFctxId(fctxId)
                        .flatMapCompletable(contact -> contactsDataManager.sendPaymentDeclinedResponse(contact.getMdid(), fctxId))
                        .doOnError(throwable -> contactsDataManager.fetchContacts())
                        .doAfterTerminate(this::setupViewModel)
                        .subscribe(
                                () -> dataListener.showToast(R.string.contacts_pending_transaction_decline_success, ToastCustom.TYPE_OK),
                                throwable -> dataListener.showToast(R.string.contacts_pending_transaction_decline_failure, ToastCustom.TYPE_ERROR)));
    }

    void confirmCancelTransaction(String fctxId) {
        compositeDisposable.add(
                contactsDataManager.getContactFromFctxId(fctxId)
                        .flatMapCompletable(contact -> contactsDataManager.sendPaymentCancelledResponse(contact.getMdid(), fctxId))
                        .doOnError(throwable -> contactsDataManager.fetchContacts())
                        .doAfterTerminate(this::setupViewModel)
                        .subscribe(
                                () -> dataListener.showToast(R.string.contacts_pending_transaction_cancel_success, ToastCustom.TYPE_OK),
                                throwable -> dataListener.showToast(R.string.contacts_pending_transaction_cancel_failure, ToastCustom.TYPE_ERROR)));
    }

    void onAccountChosen(int accountPosition, String fctxId) {
        dataListener.showProgressDialog();
        FacilitatedTransaction transaction = contact.getFacilitatedTransactions().get(fctxId);

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setIntendedAmount(transaction.getIntendedAmount());
        paymentRequest.setId(fctxId);

        compositeDisposable.add(
                getNextReceiveAddress(getCorrectedAccountIndex(accountPosition))
                        .doOnNext(paymentRequest::setAddress)
                        .flatMapCompletable(s -> contactsDataManager.sendPaymentRequestResponse(contact.getMdid(), paymentRequest, fctxId))
                        .doAfterTerminate(() -> dataListener.dismissProgressDialog())
                        .subscribe(
                                () -> {
                                    dataListener.showToast(R.string.contacts_address_sent_success, ToastCustom.TYPE_OK);
                                    setupViewModel();
                                },
                                throwable -> dataListener.showToast(R.string.contacts_address_sent_failed, ToastCustom.TYPE_ERROR)));

    }

    private void subscribeToNotifications() {
        notificationObservable = rxBus.register(NotificationPayload.class);
        compositeDisposable.add(
                notificationObservable
                        .compose(RxUtil.applySchedulersToObservable())
                        .subscribe(
                                notificationPayload -> {
                                    if (notificationPayload.getType() != null
                                            && notificationPayload.getType().equals(NotificationPayload.NotificationType.PAYMENT)) {
                                        setupViewModel();
                                    }
                                },
                                throwable -> Log.e(TAG, "subscribeToNotifications: ", throwable)));
    }

    private Observable<String> getNextReceiveAddress(int defaultIndex) {
        Account account = payloadManager.getPayload().getHdWallets().get(0).getAccounts().get(defaultIndex);
        return Observable.fromCallable(() -> payloadManager.getNextReceiveAddress(account));
    }

    private void sortAndUpdateTransactions(Collection<FacilitatedTransaction> values) {
        ArrayList<FacilitatedTransaction> facilitatedTransactions = new ArrayList<>(values);
        Collections.sort(facilitatedTransactions, new FctxDateComparator());
        Collections.reverse(facilitatedTransactions);

        dataListener.onTransactionsUpdated(facilitatedTransactions, contact.getName());
    }

    private void showErrorAndQuitPage() {
        dataListener.showToast(R.string.contacts_not_found_error, ToastCustom.TYPE_ERROR);
        dataListener.finishPage();
    }

    private int getCorrectedAccountIndex(int accountIndex) {
        // Filter accounts by active
        List<Account> activeAccounts = new ArrayList<>();
        List<Account> accounts = payloadManager.getPayload().getHdWallets().get(0).getAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            if (!account.isArchived()) {
                activeAccounts.add(account);
            }
        }

        // Find corrected position
        return payloadManager.getPayload().getHdWallets().get(0).getAccounts().indexOf(activeAccounts.get(accountIndex));
    }

    @Override
    public void destroy() {
        rxBus.unregister(NotificationPayload.class, notificationObservable);
        super.destroy();
    }
}

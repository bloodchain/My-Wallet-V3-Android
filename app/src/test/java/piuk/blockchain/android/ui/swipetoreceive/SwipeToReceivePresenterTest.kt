package piuk.blockchain.android.ui.swipetoreceive

import android.graphics.Bitmap
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import piuk.blockchain.android.R
import piuk.blockchain.android.data.currency.CryptoCurrencies
import piuk.blockchain.android.data.datamanagers.QrCodeDataManager
import piuk.blockchain.android.ui.base.UiState
import piuk.blockchain.android.util.StringUtils

class SwipeToReceivePresenterTest {

    private lateinit var subject: SwipeToReceivePresenter
    private val activity: SwipeToReceiveView = mock()
    private val swipeToReceiveHelper: SwipeToReceiveHelper = mock()
    private val qrCodeDataManager: QrCodeDataManager = mock()
    private val stringUtils: StringUtils = mock()

    @Before
    @Throws(Exception::class)
    fun setUp() {

        subject = SwipeToReceivePresenter(qrCodeDataManager, swipeToReceiveHelper, stringUtils)
        subject.initView(activity)
    }

    @Test
    @Throws(Exception::class)
    fun `onViewReady no addresses`() {
        // Arrange
        whenever(swipeToReceiveHelper.getBitcoinReceiveAddresses()).thenReturn(emptyList())
        whenever(swipeToReceiveHelper.getBitcoinAccountName()).thenReturn("Bitcoin account")
        whenever(
                stringUtils.getFormattedString(
                        R.string.swipe_receive_request,
                        CryptoCurrencies.BTC.unit
                )
        )
                .thenReturn("BTC")
        // Act
        subject.onViewReady()
        // Assert
        verify(activity).displayCoinType("BTC")
        verify(activity).displayReceiveAccount("Bitcoin account")
        verify(activity).setUiState(UiState.LOADING)
        verify(activity).setUiState(UiState.EMPTY)
    }

    @Test
    @Throws(Exception::class)
    fun `onViewReady address returned is empty`() {
        // Arrange
        val addresses = listOf("adrr0", "addr1", "addr2", "addr3", "addr4")
        whenever(swipeToReceiveHelper.getBitcoinReceiveAddresses()).thenReturn(addresses)
        whenever(swipeToReceiveHelper.getBitcoinAccountName()).thenReturn("Account")
        whenever(swipeToReceiveHelper.getNextAvailableBitcoinAddressSingle())
                .thenReturn(Single.just(""))
        whenever(
                stringUtils.getFormattedString(
                        R.string.swipe_receive_request,
                        CryptoCurrencies.BTC.unit
                )
        )
                .thenReturn("BTC")
        // Act
        subject.onViewReady()
        // Assert
        verify(activity).setUiState(UiState.LOADING)
        verify(activity).displayCoinType("BTC")
        verify(activity).displayReceiveAccount("Account")
        verify(activity).setUiState(UiState.FAILURE)
    }

    @Test
    @Throws(Exception::class)
    fun `onView ready address returned BTC`() {
        // Arrange
        val bitmap: Bitmap = mock()
        val addresses = listOf("adrr0", "addr1", "addr2", "addr3", "addr4")
        whenever(swipeToReceiveHelper.getBitcoinReceiveAddresses()).thenReturn(addresses)
        whenever(swipeToReceiveHelper.getBitcoinAccountName()).thenReturn("Account")
        whenever(swipeToReceiveHelper.getNextAvailableBitcoinAddressSingle())
                .thenReturn(Single.just("addr0"))
        whenever(stringUtils.getFormattedString(R.string.swipe_receive_request, CryptoCurrencies.BTC.unit))
                .thenReturn("BTC")
        whenever(qrCodeDataManager.generateQrCode(anyString(), anyInt()))
                .thenReturn(Observable.just(bitmap))
        // Act
        subject.onViewReady()
        // Assert
        verify(qrCodeDataManager).generateQrCode(anyString(), anyInt())
        verifyNoMoreInteractions(qrCodeDataManager)
        verify(activity).setUiState(UiState.LOADING)
        verify(activity).displayCoinType("BTC")
        verify(activity).displayReceiveAccount("Account")
        verify(activity).displayQrCode(bitmap)
        verify(activity).setUiState(UiState.CONTENT)
        verify(activity).displayReceiveAddress("addr0")
        verifyNoMoreInteractions(activity)
    }

    @Test
    @Throws(Exception::class)
    fun `address returned ETH`() {
        // Arrange
        val address = "addr0"
        val bitmap: Bitmap = mock()
        whenever(swipeToReceiveHelper.getEthReceiveAddress()).thenReturn(address)
        whenever(swipeToReceiveHelper.getEthAccountName()).thenReturn("Account")
        whenever(swipeToReceiveHelper.getEthReceiveAddressSingle())
                .thenReturn(Single.just(address))
        whenever(stringUtils.getFormattedString(R.string.swipe_receive_request, CryptoCurrencies.ETHER.unit))
                .thenReturn("ETH")
        whenever(qrCodeDataManager.generateQrCode(anyString(), anyInt()))
                .thenReturn(Observable.just(bitmap))
        // Act
        subject.currencyPosition = 1
        // Assert
        verify(qrCodeDataManager).generateQrCode(anyString(), anyInt())
        verifyNoMoreInteractions(qrCodeDataManager)
        verify(activity).setUiState(UiState.LOADING)
        verify(activity).displayCoinType("ETH")
        verify(activity).displayReceiveAccount("Account")
        verify(activity).displayQrCode(bitmap)
        verify(activity).setUiState(UiState.CONTENT)
        verify(activity).displayReceiveAddress("addr0")
        verifyNoMoreInteractions(activity)
    }

    @Test
    @Throws(Exception::class)
    fun `onView ready address returned BCH`() {
        // Arrange
        val bitmap: Bitmap = mock()
        val addresses = listOf("adrr0", "addr1", "addr2", "addr3", "addr4")
        whenever(swipeToReceiveHelper.getBitcoinCashReceiveAddresses()).thenReturn(addresses)
        whenever(swipeToReceiveHelper.getBitcoinCashAccountName()).thenReturn("Account")
        whenever(swipeToReceiveHelper.getNextAvailableBitcoinCashAddressSingle())
                .thenReturn(Single.just("addr0"))
        whenever(stringUtils.getFormattedString(R.string.swipe_receive_request, CryptoCurrencies.BCH.unit))
                .thenReturn("BCH")
        whenever(qrCodeDataManager.generateQrCode(anyString(), anyInt()))
                .thenReturn(Observable.just(bitmap))
        // Act
        subject.currencyPosition = 2
        // Assert
        verify(qrCodeDataManager).generateQrCode(anyString(), anyInt())
        verifyNoMoreInteractions(qrCodeDataManager)
        verify(activity).setUiState(UiState.LOADING)
        verify(activity).displayCoinType("BCH")
        verify(activity).displayReceiveAccount("Account")
        verify(activity).displayQrCode(bitmap)
        verify(activity).setUiState(UiState.CONTENT)
        verify(activity).displayReceiveAddress("addr0")
        verifyNoMoreInteractions(activity)
    }

}
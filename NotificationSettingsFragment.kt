/*
I have set the onClickListener on apiCallButton to callApi function
*/


package com.paytm.business.notificationsettings.fragment

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.business.common_module.constants.CommonConstants
import com.business.common_module.constants.GAConstants
import com.business.common_module.utilities.AppUtilityCommon
import com.business.common_module.utilities.LogUtility
import com.business.common_module.utilities.viewModel.LiveDataWrapper
import com.business.common_module.utilities.viewModel.Status
import com.business.merchant_payments.PaymentsConfig
import com.business.merchant_payments.businesswallet.BwSwitchConfirmationBottmsheet
import com.business.merchant_payments.commonviews.P4bToast
import com.business.merchant_payments.notificationsettings.CustomDialogUtils
import com.business.merchant_payments.notificationsettings.activity.AddMobileActivity
import com.business.merchant_payments.notificationsettings.activity.EmailAndSmsNotificationActivity
import com.business.merchant_payments.notificationsettings.model.*
import com.business.merchant_payments.notificationsettings.viewmodel.OrderListViewModel
import com.business.merchant_payments.topicPush.fullScreenNotification.LockScreenNotification
import com.business.network.NetworkFactory
import com.google.android.material.snackbar.Snackbar
import com.paytm.business.R
import com.paytm.business.app.AppConstants
import com.paytm.business.app.BusinessApplication
import com.paytm.business.common.controllers.ActivityContextController
import com.paytm.business.common.view.activity.BaseActivity
import com.paytm.business.common.view.fragment.BaseFragment
import com.paytm.business.databinding.FragmentNotificationSettingsBinding
import com.paytm.business.deeplink.DeepLinkConstant
import com.paytm.business.deeplink.DeepLinkHandler
import com.paytm.business.gtm.GAGTMTagAnalytics
import com.paytm.business.gtm.GTMConstants
import com.paytm.business.gtm.GTMDataProviderImpl
import com.paytm.business.gtm.GTMLoader
import com.paytm.business.home.network.HomeApi
import com.paytm.business.inhouse.common.webviewutils.view.P4BLockActivity
import com.paytm.business.merchantDataStore.MerchantDataProviderImpl
import com.paytm.business.network.ErrorUtil
import com.paytm.business.notification.smsSubscription.*
import com.paytm.business.notification.smsSubscription.model.SubscriptionFetchResponse
import com.paytm.business.notificationsettings.activity.ApiResponseActivity
import com.paytm.business.notificationsettings.activity.AudioAlertActivity
import com.paytm.business.notificationsettings.activity.ChatAndNotificationSettings
import com.paytm.business.notificationsettings.adapters.NotificationBannerAdapter
import com.paytm.business.notificationsettings.dialog.FullScreenNotificationDialog
import com.paytm.business.notificationsettings.utils.NotificationSettingsTargetScreenConstants
import com.paytm.business.notificationsettings.viewmodel.NotificationViewModel
import com.paytm.business.utility.DateUtility
import com.paytm.business.utility.AppUtility
import com.paytm.business.utility.NetworkUtility
import com.paytm.business.utility.RequestParamUtil
import com.paytm.business.utility.SharedPreferencesUtil
import com.paytmmall.clpartifact.modal.SanitizedResponseModel
import kotlinx.android.synthetic.main.fragment_notification_settings.view.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*

/**
 * A simple [Fragment] subclass.



 */
class NotificationSettingsFragment : BaseFragment(),
        BwSwitchConfirmationBottmsheet.BwSwitchConfirmationListener,ISMSBottomsheetCallbackListener{

    private var isFromCreateSubscription: Boolean = false
    private lateinit var mBinding: FragmentNotificationSettingsBinding
    private lateinit var mViewModel: NotificationViewModel
    private val ADD_EMAIL_REQUEST_CODE = 1001
    private val SMS_SETTINGS_REQUEST_CODE = 1002
    private val EMAIL_SETTINGS_REQUEST_CODE = 1003
    private val AUDIO_SETTINGS_REQUEST_CODE = 1004
    private var FROM_SCREEN = ""
    private var mToBeHighlightedSection = ""
    private var mViewToBeHighLighted: View? = null
    private lateinit var mBannerAdapter: NotificationBannerAdapter
    private lateinit var smsSubscriptionViewModel : SMSSubscriptionViewModel
    private var isFetchSubscriptionHasEmptyResponse = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_notification_settings, container, false)
        return mBinding.root
    }
    companion object{
        @JvmStatic
        @JvmOverloads
        fun newInstance(bundle: Bundle? = null): NotificationSettingsFragment {
            val fragment = NotificationSettingsFragment()
            bundle?.let { fragment.arguments = bundle }
            return fragment
        }
    }

    override fun onResume() {
        super.onResume()
        highlightParticularSection()
    }

    override fun initUI() {
        initBindings()
        initSMSSubscriptionViewModel()
        initObservers()
        loadNotificationSettings()
        loadStoreFrontBanner()
        launchDeepLinkScreen()
        handleSmsToggle()
    }

    private fun initSMSSubscriptionViewModel() {
        smsSubscriptionViewModel  = ViewModelProvider(requireActivity(),SMSSubscriptionViewModel
            .SMSSubscriptionViewModelFactory(BusinessApplication.getInstance(),SMSSubscriptionRepo(BusinessApplication.getInstance().kotlinNetworkService))).get(SMSSubscriptionViewModel::class.java)
       callFetchSubscriptionApi()
    }

    private fun callFetchSubscriptionApi() {
        val url = GTMLoader.getInstance(BusinessApplication.getInstance())
            .getString(GTMConstants.SMS_SUBSCRIPTION_FETCH_API)
        val headers = RequestParamUtil.getHeaders(BusinessApplication.getInstance())
        val params = getQueryParams()
        smsSubscriptionViewModel.fetchSubscriptionAPI(url,headers,params)
        lifecycleScope.launch {
            smsSubscriptionViewModel.getFetchSubscription().collect {
                when(it.status){

                    com.paytm.business.notification.smsSubscription.Status.LOADING->{
                        onLoadingState()
                    }
                    com.paytm.business.notification.smsSubscription.Status.SUCCESS->{
                        it.data?.let { it1 -> fetchSMSSubscriptionData(it1) }
                    }
                    com.paytm.business.notification.smsSubscription.Status.ERROR->{
                        onApiFailure()
                    }
                }
            }
        }

    }

    private fun getQueryParams(): HashMap<String, Any> {
        val params = HashMap<String,Any>()
        params["usn"] = SharedPreferencesUtil.getMerchantMid()
        params["subscriptionType"] = SMSConstants.RENTAL
        return params
    }

    private fun fetchSMSSubscriptionData(it: LiveDataWrapper<SubscriptionFetchResponse>) {
        (activity as BaseActivity).removeProgressDialog()
        if(it.data != null && it.data.status.equals(SMSConstants.SUCCESS,true)){
            if(it.data.results.subscriptions.isEmpty()){
                isFetchSubscriptionHasEmptyResponse = true;
                return
            }else {
                isFetchSubscriptionHasEmptyResponse = false
            }
            val subscription = it.data.results.subscriptions[0]
                when (subscription.status) {
                    SMSConstants.INACTIVE -> {
                      updateSMSInactiveCaseInUI(subscription.status)
                    }
                   SMSConstants.ACTIVE-> {
                       updateSMSActiveCaseInUI(subscription.status)
                    }
                   SMSConstants.SUSPEND -> {
                        updateSMSSuspendCaseInUI(subscription.status)
                    }
                }

                smsSubscriptionViewModel.nextDueDate.value = subscription.nextDueDate
                smsSubscriptionViewModel.endDate.value = subscription.cycleEndDate
                mViewModel.subscriptionStatus.value = subscription.status
                mViewModel.smsSubscriptionEndDate.value = subscription.cycleEndDate
                 mViewModel.smsSubscriptionNextDueDate.value = subscription.nextDueDate
                GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_CHARGES_SHOWN,
                    "",SharedPreferencesUtil.getSMSCommissionValue(context).toString() + ";"
                            + "Subscription Status " + subscription.status +";"+
                            subscription.nextDueDate
                )
            }else{
                mViewModel.isSmsAlertOn.value = false
               LogUtility.d(TAG,it.msg)
            }
    }

    private fun updateSMSInactiveCaseInUI(status: String) {
        mBinding.viewSms.btnToggleSms.alpha = 0.5f
        mViewModel.isSmsAlertOn.value = false
        mBinding.viewSms.tvReEnableSmsAlert.visibility = View.GONE
        mViewModel.subscriptionStatus.value = status
        mViewModel.getSMSToggleDescription()
    }

    private fun updateSMSActiveCaseInUI(status: String) {
        mBinding.viewSms.btnToggleSms.alpha = 1f
        mBinding.viewSms.tvReEnableSmsAlert.visibility = View.GONE
        mViewModel.isSmsAlertOn.value =true
        mBinding.viewSms.btnToggleSms.isEnabled = true
        mViewModel.subscriptionStatus.value = status
       mViewModel.getSMSToggleDescription()
        if(isFromCreateSubscription)
            openConfirmSubscriptionBottomsheet()
    }
    private fun updateSMSSuspendCaseInUI(status: String) {
        mViewModel.isSmsAlertOn.value = true
        mBinding.viewSms.btnToggleSms.isEnabled = false
        mBinding.viewSms.btnToggleSms.alpha = 0.5f
        mBinding.viewSms.tvReEnableSmsAlert.visibility = View.VISIBLE
        mViewModel.subscriptionStatus.value = status
       mViewModel.getSMSToggleDescription()
    }

    /**
     * function to handle sms toggle
     * removed earlier implementation because it was getting into loop on activation and deactivation of sms
     */
    private fun handleSmsToggle () {
        mBinding.viewSms.btnToggleSms.setOnClickListener {
            if (mViewModel.isSmsAlertOn.value!!) {
                openUnsubscriptionBottomSheet(SMSConstants.UNSUBSCRIBE_SMS_SERVICE)
            } else {
                if (mViewModel.mSMSCharges.value != "")
                    openCreateSubscriptionBottomSheet()
                else
                    mViewModel.updateNotificationSettingsOnServer(true, AppConstants.SMS_NOTIFICATION)
            }
        }
    }

    private fun openCreateSubscriptionBottomSheet() {
        val openBottomSheet = SMSCreateSubscriptionBottomSheet.newInstance(SMSConstants.SUBSCRIBE_SMS_SERVICE)
        openBottomSheet.isCancelable = true
        openBottomSheet.show(
            requireActivity().supportFragmentManager,
            "sms_create_subscription_bottomsheet"
        )
        openBottomSheet.setListener(this)
        GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_ALERT_POPUP,
            "",SharedPreferencesUtil.getSMSCommissionValue(context).toString())

    }

    private fun launchDeepLinkScreen() {
        when(arguments?.getInt(AppConstants.NOTIFICATION_SETTINGS_SUBSCREEN)){
            NotificationSettingsTargetScreenConstants.AUDIO_ALERT_SCREEN ->{
                launchAudioAlertSetting()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                ADD_EMAIL_REQUEST_CODE -> {
                    addEmailId()
                }
                AppConstants.REQUEST_CODE.ADD_PRIMARY_EMAIL -> {
                    data?.let { onPrimaryEmailAdded(it, NotificationReceiverModel.PRIMARY_EMAIL) }
                }
                AppConstants.REQUEST_CODE.ADD_SECONDARY_EMAIL -> {
                    data?.let { onPrimaryEmailAdded(it, NotificationReceiverModel.SECONDARY_EMAIL) }
                }
                SMS_SETTINGS_REQUEST_CODE -> {
                    data?.let { onSmsSettingsChanged(it) }
                }
                EMAIL_SETTINGS_REQUEST_CODE -> {
                    data?.let { onEmailSettingsChanged(it) }
                }
                AUDIO_SETTINGS_REQUEST_CODE -> {
                    checkIfAudioLanguageWasSaved(data)
                    updateAudioAlertInfo()
                }
            }
        }
    }

    private fun checkIfAudioLanguageWasSaved(data: Intent?) {
        if(null != data && data.getBooleanExtra(AppConstants.IF_AUDIO_SETTINGS_SAVE_PREF_FLOW,false)) {
            mViewModel.isReturnedFromAudioFrag.value = true
            mViewModel.isAudioAlertOn.value = true
        }
    }

    private fun initBindings(){
        mBinding.lifecycleOwner = this
        mViewModel = ViewModelProvider(this).get(NotificationViewModel::class.java)
        mBinding.viewmodel = mViewModel
        if (activity?.intent?.hasExtra(AppConstants.NOTIFICATION_DETAILS.FROM) == true)
            FROM_SCREEN = activity?.intent?.getStringExtra(AppConstants.NOTIFICATION_DETAILS.FROM)!!
        if (activity?.intent?.hasExtra(DeepLinkConstant.KEY_DEEPLINK_HIGHLIGHT_SECTION) == true)
            mToBeHighlightedSection = activity?.intent?.getStringExtra(DeepLinkConstant.KEY_DEEPLINK_HIGHLIGHT_SECTION)!!
//        if(!MerchantDataProviderImpl.isMerchantAdmin() || !MerchantDataProviderImpl.isMerchant100k()){
//            mBinding.textviewSettings.visibility = View.VISIBLE
//            mBinding.headerNotificationSettings.visibility = View.VISIBLE
//            mBinding.imageNotification.setTopMargin(AppUtility.convertDpToPixel(20f, activity))
//        }
        mBinding.root.findViewById<View>(R.id.view_email).findViewById<View>(R.id.bottom_separator).visibility = View.GONE
        mBinding.clHelp.setOnClickListener {
            GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_NEED_HELP_CLICKED, "","")
            DeepLinkHandler(activity as Activity).handleUrl(GTMDataProviderImpl.getInstance(requireContext()).getString(GTMConstants.DEEPLINK_NEED_HELP_NOTIF), true)
        }

        mBinding.viewSms.tvReEnableSmsAlert.setOnClickListener {
            openUnsubscriptionBottomSheet(SMSConstants.ENABLE_SMS_SERVICE)
            GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_RENABLE_CLICKED,
                "",SharedPreferencesUtil.getSMSCommissionValue(context).toString(),"",
                smsSubscriptionViewModel.endDate.value?.let { it1 ->
                    DateUtility.getDaysFromToday(
                        DateUtility.INPUT_DATE_FORMAT_ONLINE, it1
                    ).toString()
                })

        }


        //Setting click listener on api call button
        mBinding.apiCallButton.setOnClickListener{
            try{
                var noOfPages = Integer.parseInt(mBinding.noOfPagesEdittext.text.toString())
                mBinding.noOfPagesEdittext.text = null

                if(noOfPages < 1 || noOfPages > 50){
                    noOfPages = 50
                }
                callApi(noOfPages)
            }catch(exception : Exception){
                Log.e("Error", exception.toString())
            }
        }

        mViewModel.isFullScreenNotificationOnDialogObserver.observe(this,  {
            if (it) {
                val fragment = FullScreenNotificationDialog()
                activity?.supportFragmentManager?.let { fragment.show(it,"full_screen_notification_dialog") }
            }
        })
    }
    //Click listener for api call button
    private fun callApi(pageSize : Int){
        val tag = "API_CALL"
        var response : OrderResponseModel?

        val result = lifecycleScope.async{
            try{
                mBinding.apiCallButton.text = getString(R.string.loading)
                val headers = RequestParamUtil.getRequestHeaderMidParam(PaymentsConfig.getInstance().appContext)
                response = HomeApi.retrofitObj.getHomeApiData(headers, pageSize)
                response
            }catch(exception : Exception){
                null
            }
        }

        lifecycleScope.launch{
            OrderListViewModel.orderList = result.await()?.orderList?.orderList
            openNewActivity()
            mBinding.apiCallButton.text = getString(R.string.call_api)
        }
    }

    private fun openNewActivity(){
        val intent : Intent = Intent(activity, ApiResponseActivity::class.java)
        startActivity(intent)
    }

    fun View.setTopMargin(topMargin: Int) {
        val params = layoutParams as ViewGroup.MarginLayoutParams
        params.setMargins(params.leftMargin, topMargin, params.rightMargin, params.bottomMargin)
        layoutParams = params
    }

    private fun initObservers(){

        // Notification settings observer
        mViewModel.notificationSettingsLiveData.observe(viewLifecycleOwner, { value ->
            setNotificationSettings(value)
        })

        // Update Notification settings observer
        mViewModel.updateNotificationSettingsLiveData.observe(viewLifecycleOwner, { value ->
            handleUpdatedSettings(value)
        })

        //Update SMS settings and sms charges
        mViewModel.mBannerDetailstAPIResponse.observe(viewLifecycleOwner, {
            setUIForSMSCharges(it)
        })

        mViewModel.mSMSCharges.observe(viewLifecycleOwner, {
            setTextForSMSCharges(it)
        })



        if(GTMDataProviderImpl.getInstance(mContext).getBoolean(GTMConstants.SHOW_NEED_HELP_IN_NOTIF)){
            mViewModel.shouldShowNeedHelp.set(true)
        }

        // UI Events observer
        mViewModel.EVENT.observe(this, { event ->
            when (event) {
                NotificationViewModel.LAUNCH_SMS_SETTINGS -> {
                    val bundle = Bundle().apply {
                        putInt(AppConstants.NOTIFICATION_TYPE, AppConstants.SMS_NOTIFICATION)
                        val list =  mViewModel.getSmsNotificationSettings()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            list.removeIf { it.receiverType==101}
                        }else{
                           list.forEach {
                               if(it.receiverType == 101)
                                   list.remove(it)
                           }
                        }
                        list.add(if (list.isNotEmpty()) 1 else 0,NotificationReceiverModel(AppConstants.STORE_STAFF,101))
                        putSerializable(AppConstants.NOTIFICATION_SETIINGS,list)
                    }
                    startActivityForResult(Intent(activity, EmailAndSmsNotificationActivity::class.java).apply {
                        putExtra(AppConstants.NOTIFICATION_LIST, bundle)
                    }, SMS_SETTINGS_REQUEST_CODE)
                }

                NotificationViewModel.LAUNCH_EMAIL_SETTINGS -> {
                    val bundle = Bundle().apply {
                        putInt(AppConstants.NOTIFICATION_TYPE, AppConstants.EMAIL_NOTIFICATION)
                        putSerializable(AppConstants.NOTIFICATION_SETIINGS, mViewModel.getEmailNotificationSettings())
                    }
                    startActivityForResult(Intent(activity, EmailAndSmsNotificationActivity::class.java).apply {
                        putExtra(AppConstants.NOTIFICATION_LIST, bundle)
                    }, EMAIL_SETTINGS_REQUEST_CODE)
                }

                NotificationViewModel.LAUNCH_AUDIO_ALERT_SETTINGS -> {
                    launchAudioAlertSetting()
                }
                NotificationViewModel.LAUNCH_AUDIO_ALERT_SETTINGS_WITH_DELAY ->{
                    Handler().postDelayed({
                       launchAudioAlertSetting()
                    },200)
                }

                NotificationViewModel.ON_BACK_PRESS -> {
                    activity?.finish()
                }

                NotificationViewModel.ADD_EMAIL -> {
                    if (AppUtility.isAppLockEnabled(requireActivity())) {
                        addEmailId()
                    }
                    else {
                        startActivityForResult(Intent(activity, P4BLockActivity::class.java), ADD_EMAIL_REQUEST_CODE)
                    }
                }

                NotificationViewModel.SHOW_EMAIL_DEACTIVATE_DIALOG -> {
                    showEmailDeactivationDialog()
                }

                NotificationViewModel.SHOW_SMS_DEACTIVATE_DIALOG -> {
                    if(SharedPreferencesUtil.getSMSCommissionValue(requireContext()) >0
                        && mViewModel.isSmsAlertOn.value == true
                        && isFetchSubscriptionHasEmptyResponse ){
                        showSmsDeactivationDialog()
                    }else {
                        openUnsubscriptionBottomSheet(SMSConstants.UNSUBSCRIBE_SMS_SERVICE)
                        GAGTMTagAnalytics.getSingleInstance()
                            .sendEvent(BusinessApplication.getInstance(),
                                GAConstants.EVENT_CATEGORY_NOTIFICATIONS,
                                GAConstants.EVENT_ACTION_SMS_ALERT_DISABLE,
                                "",
                                getChargesTextForGA() + ";" + SharedPreferencesUtil.getSMSCommissionValue(
                                    BusinessApplication.getInstance()
                                ).toString(),
                                "",
                                smsSubscriptionViewModel.nextDueDate.value?.let {
                                    DateUtility.getDaysFromToday(
                                        DateUtility.INPUT_DATE_FORMAT_ONLINE,
                                        it
                                    )
                                        .toString()
                                })
                    }
                }

                NotificationViewModel.SHOW_SMS_ACTIVATE_DIALOG -> {
                    openCreateSubscriptionBottomSheet()
                    GAGTMTagAnalytics.getSingleInstance().sendEvent(BusinessApplication.getInstance(), GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_ALERT_ENABLE,
                        "", getChargesTextForGA() +  ";" +
                        SharedPreferencesUtil.getSMSCommissionValue(BusinessApplication.getInstance()).toString())
                }

                NotificationViewModel.NO_INTERNET_UPDATE_SETTINGS -> {
                    (activity as BaseActivity).showSnackBar(mBinding.root, getString(R.string.no_internet), null, Snackbar.LENGTH_LONG) { mViewModel.updateNotificationSettingsTryAgain() }
                }

                NotificationViewModel.NO_PERMISSION_ERROR -> {
                    Toast.makeText(BusinessApplication.getInstance().appContext, resources.getString(R.string.access_denied_error), Toast.LENGTH_LONG).show()
                }

                NotificationViewModel.LAUNCH_LOCK_SCREEN -> {
                    val intent = Intent(activity, LockScreenNotification::class.java)
                    intent.putExtra(CommonConstants.INTENT_EXTRA,AppConstants.TAG_SAMPLE)
                    startActivity(intent)
                    GAGTMTagAnalytics.getSingleInstance().sendEvent(activity,
                            GAConstants.EVENT_CATEGORY_NOTIFICATIONS,
                            GAConstants.EVENT_ACTION_FULL_VIEW_SAMPLE, "",
                            if (SharedPreferencesUtil.getIsLockScreenNotificationEnabled(context)) GAConstants.EVENT_LABEL_ON else GAConstants.EVENT_LABEL_OFF)
                }
                NotificationViewModel.SET_SELECTED_LANGUAGE ->{
                    setAudioAlertInfo()
                }

                NotificationViewModel.BW_SWITCH_AND_SMS_ACTIVATE_SUCCESS ->{
                    P4bToast.get(
                            getString(com.business.merchant_payments.R.string.bw_swich_and_activate_success),
                            true,
                            true
                    ).show(activity)
                }
            }
        })
    }
    private fun getChargesTextForGA() = if(SharedPreferencesUtil.getSMSCommissionValue(requireContext()) > 0){
        "Charges Shown"
    }else{
        "Charges Not Shown"
    }

    private fun openUnsubscriptionBottomSheet(bottomSheetType : Int) {
        val openBottomSheet = SMSUnsubscriptionRenableBottomSheet.newInstance(bottomSheetType)
        openBottomSheet.isCancelable = true
        openBottomSheet.show(
            requireActivity().supportFragmentManager,
            "sms_un_subscription_bottomsheet"
        )
        openBottomSheet.setListener(this)
        when(bottomSheetType){
            SMSConstants.UNSUBSCRIBE_SMS_SERVICE->{
                GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_DISABLED_POPUP,
                    "",SharedPreferencesUtil.getSMSCommissionValue(context).toString(),"",
                    smsSubscriptionViewModel.nextDueDate.value?.let {
                        DateUtility.getDaysFromToday(DateUtility.INPUT_DATE_FORMAT_ONLINE, it)
                            .toString()
                    }
                )
            }
            SMSConstants.ENABLE_SMS_SERVICE->{
                GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_RENABLE_POPUP,
                    "",SharedPreferencesUtil.getSMSCommissionValue(context).toString(),"",
                    smsSubscriptionViewModel.endDate.value?.let {
                        DateUtility.getDaysFromToday(
                            DateUtility.INPUT_DATE_FORMAT_ONLINE, it
                        ).toString()
                    }
                )
            }
        }
    }

    private fun launchAudioAlertSetting(){
        startActivityForResult(Intent(requireActivity(), AudioAlertActivity::class.java), AUDIO_SETTINGS_REQUEST_CODE)
        GAGTMTagAnalytics.getSingleInstance().sendEvent(BusinessApplication.getInstance(),
                GAConstants.EVENT_CATEGORY_AUDIO_ALERT_PAGE,
                GAConstants.EVENT_ACTION_AUDIO_ALERT_TRANSACTION,
                "",
                "")
    }

    private fun setNotificationSettings(notificationSettings: LiveDataWrapper<NotificationsSettingsDataModel>?) {
        notificationSettings?.let {
            when (it.status) {
                Status.LOADING -> {
                    (activity as BaseActivity).showProgressDialog()
                    return
                }
                Status.OFFLINE -> {
                    (activity as BaseActivity).removeProgressDialog()
                    mViewModel.clearStoredNotificationSettingsOnError()
                    (activity as BaseActivity).showSnackBar(mBinding.root, getString(R.string.no_internet), getString(R.string.retry), Snackbar.LENGTH_INDEFINITE) { loadNotificationSettings() }
                    return
                }
                Status.ERROR, Status.FAILURE -> {
                    (activity as BaseActivity).removeProgressDialog()
                    mViewModel.clearStoredNotificationSettingsOnError()
                    ErrorUtil.handleInvalidToken(NetworkFactory.SERVER_UMP, it.response)
                    (activity as BaseActivity).showSnackBar(mBinding.root, ErrorUtil.getErrorMessage(it.response), getString(R.string.retry), Snackbar.LENGTH_INDEFINITE) { loadNotificationSettings() }
                }
                Status.SUCCESS -> {
                    it.data?.let {
                        mViewModel.showNotificationSettings(it)
                    }
                        Handler().postDelayed({
                            if(activity !=null)
                                (activity as BaseActivity).removeProgressDialog()
                                              },300)
                }

            }
        }
    }

    private fun loadNotificationSettings() {
        if (NetworkUtility.isNetworkAvailable())
            mViewModel.getNotificationSettings()
        else
            (activity as BaseActivity).showSnackBar(mBinding.root, getString(R.string.no_internet), getString(R.string.retry), Snackbar.LENGTH_INDEFINITE) { loadNotificationSettings() }
    }

    /**
     * function to call storeFront API to get desired reponse for the merchant
     */
    private fun loadStoreFrontBanner() {
        if (NetworkUtility.isNetworkAvailable()) {
            mViewModel.getNotificationBannerDetails()
        } else
            (activity as BaseActivity).showSnackBar(mBinding.root, getString(R.string.no_internet), getString(R.string.retry), Snackbar.LENGTH_INDEFINITE) { loadNotificationSettings() }
    }

    /**
     * Show alert dialog when user deactivates from email alert
     */
    private fun showEmailDeactivationDialog() {
        CustomDialogUtils.showCustomActionDialog(requireActivity(),
                getString(R.string.notification_deactivation_title, getString(R.string.email)),
                String.format(getString(R.string.notification_deactivation_msg), getString(R.string._email), getString(R.string.payment_refunds)),
                getString(R.string.deactivate_text),
                getString(R.string.cancel),
                false,
                true,
                GAConstants.EVENT_ACTION_EMAIL_DEACTIVATION_POPUP_OPTION_SELECTED,
                GAConstants.EVENT_CATEGORY_NOTIFICATIONS,
                { mViewModel.updateNotificationSettingsOnServer(false, AppConstants.EMAIL_NOTIFICATION) }) {
            mViewModel.revertSwitchOffAction(AppConstants.EMAIL_NOTIFICATION)
        }
        GAGTMTagAnalytics.getSingleInstance().sendEvent(BusinessApplication.getInstance(),
                GAConstants.EVENT_CATEGORY_NOTIFICATIONS,
                GAConstants.EVENT_ACTION_EMAIL_DEACTIVATION_POPUP_IMPRESSION,
                "",
                "")
    }

    /**
     * Show alert dialog when user deactivates from email alert
     */
    private fun showSmsDeactivationDialog() {
        CustomDialogUtils.showCustomActionDialog(requireContext(),
                getString(R.string.notification_deactivation_title, getString(R.string.sms)),
                if (mViewModel.mSMSCharges.value != "") getString(R.string.sms_notification_deactivation_msg, "${mViewModel.mSMSCharges.value}")
                else String.format(getString(R.string.notification_deactivation_msg), getString(R.string._sms), getString(R.string.payment_refunds)),
                getString(R.string.deactivate_text),
                getString(R.string.cancel),
                false,
                true,
                GAConstants.EVENT_ACTION_SMS_DEACTIVATION_POPUP_OPTION_SELECTED,
                GAConstants.EVENT_CATEGORY_NOTIFICATIONS,
                {
                    mViewModel.updateNotificationSettingsOnServer(false, AppConstants.SMS_NOTIFICATION)
                    mViewModel.isSmsAlertOn.value = false}) {
            mViewModel.revertSwitchOffAction(AppConstants.SMS_NOTIFICATION)
        }
        GAGTMTagAnalytics.getSingleInstance().sendEvent(BusinessApplication.getInstance(),
                GAConstants.EVENT_CATEGORY_NOTIFICATIONS,
                GAConstants.EVENT_ACTION_SMS_DEACTIVATION_POPUP_IMPRESSION,
                "",
                "")
    }




    fun onSmsActivationConsent(){


        if(!mViewModel.switchToBwConsentGiven &&
                PaymentsConfig.getInstance().merchantDataProvider.hasInstantSettleUpdatePermissions()
        ){

            BwSwitchConfirmationBottmsheet
                    .getInstance(SMS_SETTINGS_REQUEST_CODE).
                    show(childFragmentManager,
                            BwSwitchConfirmationBottmsheet::class.simpleName)
            return
        }

        mViewModel.updateNotificationSettingsOnServer(true, AppConstants.SMS_NOTIFICATION)
        mViewModel.isSmsAlertOn.value = true
    }
    /**
     * Handle updated settings on UI
     */
    private fun handleUpdatedSettings(updatedSettings: LiveDataWrapper<NotificationOnOffDataModel>?) {
        updatedSettings?.let {
            when (it.status) {
                Status.LOADING -> {
                    (activity as BaseActivity).showProgressDialog()
                    return
                }
                Status.OFFLINE -> {
                    (activity as BaseActivity).removeProgressDialog()
                    mViewModel.revertSwitchActions()
                    (activity as BaseActivity).showSnackBar(mBinding.root, getString(R.string.no_internet), null, Snackbar.LENGTH_LONG) { mViewModel.updateNotificationSettingsTryAgain() }
                    return
                }
                Status.ERROR, Status.FAILURE -> {
                    (activity as BaseActivity).removeProgressDialog()
                    mViewModel.revertSwitchActions()
                    ErrorUtil.handleInvalidToken(NetworkFactory.SERVER_UMP, it.response)
                    (activity as BaseActivity).showSnackBar(mBinding.root, ErrorUtil.getErrorMessage(it.response), null, Snackbar.LENGTH_LONG) { mViewModel.updateNotificationSettingsTryAgain() }
                    return
                }
                Status.SUCCESS -> {
                    (activity as BaseActivity).removeProgressDialog()
                    it.data?.let {
                        mViewModel.updateNotificationSettingsOnResponse(it)
                    }
                }
            }
        }
    }

    /**
     * function to setup UI to be shown related to sms charges on the basis of reponse received from
     * storefront API
     */
    private fun setUIForSMSCharges(sanitzedResponseModel: SanitizedResponseModel) {
        if (sanitzedResponseModel != null) {
            for (widgets in sanitzedResponseModel.rvWidgets) {
                if (widgets.type == "banner-3_0") {
                    //case : no items to show in banner, then remove recycler view
                    if (widgets.items.size <= 0) {
                        mBinding.rvStorefrontBanner.visibility = View.GONE
                    } else {//case : when more than 0 banners are present
                        mBinding.rvStorefrontBanner.visibility = View.VISIBLE
                    }
                }
            }

            mBannerAdapter = NotificationBannerAdapter(sanitzedResponseModel.rvWidgets as ArrayList<Any>)
            mBinding.rvStorefrontBanner.layoutManager = LinearLayoutManager(context)
            mBinding.rvStorefrontBanner.adapter = mBannerAdapter
            mBinding.rvStorefrontBanner.itemAnimator = DefaultItemAnimator()
            mBannerAdapter.mGAListener = sanitzedResponseModel.gaListener
        } else {//case : when no data is retrieved from the storefront
            mBinding.rvStorefrontBanner.visibility = View.GONE
        }
    }

    private fun setTextForSMSCharges(amount: String) {
        if ("0".equals(amount,true) ) {
            mBinding.viewSms.llDescSmsCharges.visibility = View.GONE
        } else {
            mBinding.viewSms.llDescSmsCharges.visibility = View.VISIBLE
            mBinding.viewSms.tvSmsCharges.text = mViewModel.getSMSToggleDescription()
        }
    }

    /**
     * Highlight particular section on launch
     */
    private fun highlightParticularSection() {
        if (mToBeHighlightedSection.isNotEmpty()) {
            val VOICE_NOTIFICATION = "voice_notification"
            val FULL_SCREEN_NOTIFICATION = "full_screen_notification"
            val ADD_ANOTHER_MOBILE = "add_another_mobile"
            val SMS_CHARGES_SETTINGS = "sms_charges_settings"
            val ADD_ANOTHER_EMAIL = "add_another_email"
            val NOTIFICATION_SETTINGS = "notification_settings"
            val NOTIFICATION__SETTINGS = "notification-settings"
            when (mToBeHighlightedSection) {
                VOICE_NOTIFICATION -> mViewToBeHighLighted = mBinding.audioSettingsOverlay
                FULL_SCREEN_NOTIFICATION -> mViewToBeHighLighted = mBinding.lockNotificationOverlay
                ADD_ANOTHER_MOBILE -> mViewToBeHighLighted = mBinding.smsSettingsOverlay
                ADD_ANOTHER_EMAIL -> mViewToBeHighLighted = mBinding.emailSettingsOverlay
                NOTIFICATION_SETTINGS, NOTIFICATION__SETTINGS -> mViewToBeHighLighted = mBinding.notificationSettingsOverlay
                SMS_CHARGES_SETTINGS -> mViewToBeHighLighted = mBinding.smsChargesSettingsOverlay
            }
            if (mViewToBeHighLighted != null) {
                Handler().postDelayed({
                    context?.let { AppUtilityCommon.highlightView(mViewToBeHighLighted!!, false, it) }
                    if (ActivityContextController.getInstance().currentActivity is ChatAndNotificationSettings)
                        mToBeHighlightedSection = ""
                }, 1000)
            }
        }
    }

    /**
     * Add primary email address
     */
    private fun addEmailId() {
        val intent = Intent(activity, AddMobileActivity::class.java)
        if (MerchantDataProviderImpl.hasAddPrimaryDetailPermission()) {
            intent.putExtra(AppConstants.NOTIFICATION_DETAILS.TYPE, AppConstants.NOTIFICATION_DETAILS.TYPE_PRIMARY_EMAIL)
            startActivityForResult(intent, AppConstants.REQUEST_CODE.ADD_PRIMARY_EMAIL)
        } else {
            intent.putExtra(AppConstants.NOTIFICATION_DETAILS.TYPE, AppConstants.NOTIFICATION_DETAILS.TYPE_SECONDARY_EMAIL)
            startActivityForResult(intent, AppConstants.REQUEST_CODE.ADD_SECONDARY_EMAIL)
        }
    }

    /**
     * Update Email info after addition
     */
    private fun onPrimaryEmailAdded(data: Intent, emailType: Int) {
        val updatedValue = data.getStringExtra(AppConstants.NOTIFICATION_DETAILS.UPDATED_VALUE)
        mViewModel.updatePrimaryEmail(updatedValue, emailType)
    }

    /**
     * Handle changed notification settings
     */
    private fun onEmailSettingsChanged(settings: Intent) {
        if (settings.hasExtra(AppConstants.NOTIFICATION_LIST)) {
            val bundle = settings.getBundleExtra(AppConstants.NOTIFICATION_LIST)
            if (bundle.containsKey(AppConstants.NOTIFICATION_SETIINGS)) {
                val notificationSettings = bundle.getSerializable(AppConstants.NOTIFICATION_SETIINGS) as ArrayList<NotificationReceiverModel>
                mViewModel.updateEmailSettings(notificationSettings)
            }
        }
    }

    /**
     * Handle changed Sms Settings
     */
    private fun onSmsSettingsChanged(settings: Intent) {
        if (settings.hasExtra(AppConstants.NOTIFICATION_LIST)) {
            val bundle = settings.getBundleExtra(AppConstants.NOTIFICATION_LIST)
            if (bundle.containsKey(AppConstants.NOTIFICATION_SETIINGS)) {
                val notificationSettings = bundle.getSerializable(AppConstants.NOTIFICATION_SETIINGS) as ArrayList<NotificationReceiverModel>
                mViewModel.updateSmsSettings(notificationSettings)
            }
        }
    }

    private fun updateAudioAlertInfo() {
        setAudioAlertInfo()
    }

    /**
     * Set Audio Alert Info for UI
     */
    private fun setAudioAlertInfo() {

        when(SharedPreferencesUtil.getVoiceLocale(BusinessApplication.getInstance())){
            AppConstants.HINDI_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.hindi)
            }
            AppConstants.ENGLISH_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.english)
            }
            AppConstants.MALYALAM_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.malyalam)
            }
            AppConstants.PUNJABI_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.punjabi)
            }
            AppConstants.MARATHI_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.marathi)
            }
            AppConstants.KANNADA_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.kannada)
            }
            AppConstants.TAMIL_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.tamil)
            }
            AppConstants.TELUGU_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.telugu)
            }
            AppConstants.ORIYA_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.oriya)
            }
            AppConstants.BANGALI_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.bangla)
            }
            AppConstants.GUJRATI_CODE ->{
                mViewModel.currentSelectedLanguage.value = resources.getString(R.string.gujrati)
            }
        }
    }

    override fun onCBwSwitchClose(requestId: Int, consentGiven: Boolean) {
        if(requestId == SMS_SETTINGS_REQUEST_CODE){

            if(consentGiven){
                mViewModel.switchToBwConsentGiven = true
                onSmsActivationConsent()
            }
            else{
                mViewModel.revertSwitchOnAction(AppConstants.SMS_NOTIFICATION)
            }
        }


    }

    override fun onBottomSheetCancelled() {
        callFetchSubscriptionApi()
    }

    override fun onUnsubscribeNowCallback() {
        (activity as BaseActivity).removeProgressDialog()
        this.isFromCreateSubscription = false
        callFetchSubscriptionApi()
    }

    override fun onSubscribeNowClicked() {
       (activity as BaseActivity).removeProgressDialog()
        this.isFromCreateSubscription = false
        callFetchSubscriptionApi()
    }

    override fun onCreateSubscriptionSubscribeNowClicked(isFromCreateSubscription: Boolean) {
        (activity as BaseActivity).removeProgressDialog()
        this.isFromCreateSubscription = isFromCreateSubscription
        callFetchSubscriptionApi()
    }

    override fun onGoToHomeClicked() {
        activity?.finish()
        GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_ACTIVATED_GO_TO_HOME_CLICKED,
            "",SharedPreferencesUtil.getSMSCommissionValue(context).toString())
    }

    override fun onMyServicesClicked() {
        DeepLinkHandler(requireActivity()).handleUrl(DeepLinkConstant.MY_SERVICES_DEEPLINK, false)
        GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_ACTIVATED_MY_SERVICES_CLICKED,
            "",SharedPreferencesUtil.getSMSCommissionValue(context).toString())
    }

    override fun onCancelButtonClicked() {
        mViewModel.isSmsAlertOn.value = true
    }

    override fun onLoadingState() {
        (activity as BaseActivity).showProgressDialog()
    }

    override fun onApiFailure() {
        (activity as BaseActivity).removeProgressDialog()
        Toast.makeText(requireContext(),getString(R.string.error_msg_default),Toast.LENGTH_SHORT).show()
    }

    override fun onOfflineState() {
        (activity as BaseActivity).removeProgressDialog()
        Toast.makeText(requireContext(),getString(R.string.error_msg_unknown_host),Toast.LENGTH_SHORT).show()
    }

    private fun openConfirmSubscriptionBottomsheet(){
        val openBottomSheet = SMSConfirmSubscriptionBottomSheet.newInstance(SMSConstants.SMS_SERVICE_ACTIVATION)
        openBottomSheet.isCancelable = true
        openBottomSheet.show(requireActivity().supportFragmentManager, "sms_confirm_subscription_bottomsheet")
        openBottomSheet.setListener(this)
        GAGTMTagAnalytics.getSingleInstance().sendEvent(activity, GAConstants.EVENT_CATEGORY_NOTIFICATIONS, GAConstants.EVENT_ACTION_SMS_ACTIVATED_POPUP,
            "",SharedPreferencesUtil.getSMSCommissionValue(context).toString())
    }





}


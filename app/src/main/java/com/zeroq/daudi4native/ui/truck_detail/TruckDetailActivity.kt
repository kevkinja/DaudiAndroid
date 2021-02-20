package com.zeroq.daudi4native.ui.truck_detail

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.zeroq.daudi4native.R
import com.zeroq.daudi4native.commons.BaseActivity
import com.zeroq.daudi4native.data.models.*
import com.zeroq.daudi4native.ui.printing.PrintingActivity
import com.zeroq.daudi4native.utils.ActivityUtil
import com.zeroq.daudi4native.utils.ImageUtil
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_truck_detail.*
import kotlinx.android.synthetic.main.activity_truck_detail.btnPrint
import kotlinx.android.synthetic.main.activity_truck_detail.content_scroll
import kotlinx.android.synthetic.main.activity_truck_detail.layout_constraint
import kotlinx.android.synthetic.main.activity_truck_detail.tv_ago
import kotlinx.android.synthetic.main.activity_truck_detail.tv_ik
import kotlinx.android.synthetic.main.activity_truck_detail.tv_pms
import kotlinx.android.synthetic.main.activity_truck_detail.tv_today_date
import kotlinx.android.synthetic.main.activity_truck_detail.tv_truck_id
import kotlinx.android.synthetic.main.toolbar.toolbar
import net.glxn.qrgen.android.QRCode
import org.jetbrains.anko.toast
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

class TruckDetailActivity : BaseActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var imageUtil: ImageUtil

    @Inject
    lateinit var activityUtil: ActivityUtil


    lateinit var truckDetailViewModel: TruckDetailViewModel

    private val _fuelTypeList = ArrayList<String>()


    // set data to compartments
    private lateinit var viewComp: List<EditText>

    private lateinit var btnComp: List<AppCompatButton>
    private lateinit var _topInputs: List<EditText>

    private lateinit var _user: UserModel
    // private var DepotTruck: TruckModel? = null
    private var depotOrder: OrderModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_truck_detail)


        /**
         * hide keyboard
         * */
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        viewComp = listOf(
            et_c1_qty, et_c2_qty,
            et_c3_qty, et_c4_qty, et_c5_qty, et_c6_qty, et_c7_qty
        )

        btnComp = listOf(
            et_c1_type, et_c2_type,
            et_c3_type, et_c4_type, et_c5_type, et_c6_type, et_c7_type
        )

        _topInputs = listOf(et_driver_name, et_driver_id, et_driver_plate)

        /*
        * set  the viewModel
        * */
        truckDetailViewModel = getViewModel(TruckDetailViewModel::class.java)
        intent.getStringExtra("ORDER_ID")?.let { id ->
            truckDetailViewModel.setOrderId(id)
        }


        truckDetailViewModel.getUser().observe(this, Observer {
            if (it.isSuccessful) {
                _user = it.data()!!
                truckDetailViewModel.setUserModel(_user);
                truckDetailViewModel.setDepotId(_user.config?.app?.depotid.toString())
            } else {
                Timber.e(it.error()!!)
            }
        })

        truckDetailViewModel.getDepot().observe(this, Observer {
            if (it.isSuccessful) {

                it.data()?.let { depo ->
                    tv_depot_name_d.text = "[ ${depo.Name} ]"
                }
            } else {
                Timber.e(it.error())
            }
        })

        truckDetailViewModel.getOrder().observe(this, Observer {
            if (it.isSuccessful) {
                depotOrder = it.data()
                initialOrderValues(it.data()!!)
            } else {
                Timber.e(it.error())
            }
        })




        initToolbar()
        topInputs()
        compartimentsButtonOps()
        compartmentsInputs()
        initProgress()
    }


    private fun initialOrderValues(order: OrderModel) {

        _fuelTypeList.clear()
        _fuelTypeList.add("EMPTY")

        if (order.fuel?.pms?.qty != 0) _fuelTypeList.add("PMS")
        if (order.fuel?.ago?.qty != 0) _fuelTypeList.add("AGO")
        if (order.fuel?.ik?.qty != 0) _fuelTypeList.add("IK")

        tv_truck_id.text = order.QbConfig?.InvoiceNumber
        
        tv_customer_value.text = order.customer?.name
        et_driver_name.setText(order.truck?.driverdetail?.name)
        et_driver_id.setText(order.truck?.driverdetail?.id)
        et_driver_plate.setText(order.truck?.truckdetail?.numberplate)

        // fuel
        tv_pms.text = "PMS [ " + order.fuel?.pms?.qty + " ]"
        tv_ago.text = "AGO [ " + order.fuel?.ago?.qty + " ]"
        tv_ik.text = "IK      [ " + order.fuel?.ik?.qty + " ]"


        // fuel entries


        tv_pms_entry.text = getBatchName(order.fuel?.pms!!)
        tv_ago_entry.text = getBatchName(order.fuel?.ago!!)
        tv_ik_entry.text = getBatchName(order.fuel?.ik!!)




        order.truck?.compartments?.forEachIndexed { index, compartment ->
            if (compartment.qty != null && compartment.qty != 0) {
                btnComp[index].text = compartment.fueltype?.toUpperCase()
                viewComp[index].setText(compartment.qty!!.toString())
            } else {
                btnComp[index].text = "EMPTY"
                viewComp[index].text = null
                viewComp[index].hint = "Empty Comp"
                viewComp[index].isEnabled = false
            }
        }

        tv_auth_by_value.text = order.truckStageData!!["1"]?.user?.name

        // display name
        tv_confirmed_by_value.text = firebaseAuth.currentUser?.displayName


        /**
         * current data, will change it later to be regenerated before printing
         * */
        val sdf = SimpleDateFormat("dd/M/yyyy hh:mm aaa")
        tv_today_date.text = sdf.format(Date()).toUpperCase()

        /**
         * create qr
         * */


        val depotUrl = "https://daudi.africa/orders/${depotOrder?.Id}"

        val dimensions = imageUtil.dpToPx(this, 150)

        val thread = Thread(Runnable {
            val myBitmap = QRCode.from(depotUrl)
                .withSize(dimensions, dimensions)
                .bitmap()

            runOnUiThread {
                qr.setImageBitmap(myBitmap)
            }
        })

        thread.start()

        btnPrint.setOnClickListener {
            progressDialog.show()
            requestPermissions()
        }

        /**
         * disable views if the truck is already printed
         * */
        if (order.printStatus?.LoadingOrder?.status != null
            && order.printStatus?.LoadingOrder?.status!!
        ) {
            activityUtil.disableViews(layout_constraint)
            btnPrint.isEnabled = true
        }

    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
    }

    private lateinit var progressDialog: Dialog
    private fun initProgress() {
        progressDialog = Dialog(this@TruckDetailActivity)
        progressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        progressDialog.setContentView(R.layout.custom_progress_dialog)
        progressDialog.setCancelable(false)
    }


    private fun topInputs() {
        _topInputs.forEach {
            it.filters = arrayOf<InputFilter>(InputFilter.AllCaps())

            it.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    if (s.isNullOrEmpty()) {
                        it.error = "This field cant be blank"
                    } else {
                        it.error = null
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    if (s.isNullOrEmpty()) {
                        it.error = "This field cant be blank"
                    } else {
                        it.error = null
                    }
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s.isNullOrEmpty()) {
                        it.error = "This field cant be blank"
                    } else {
                        it.error = null
                    }
                }
            })
        }
    }


    private fun compartimentsButtonOps() {
        /**
         * make sure the compartment values tally with the given fuel
         * */

        btnComp.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                var index = _fuelTypeList.indexOf(btn.text)
                index++


                if (index > (_fuelTypeList.size - 1)) {
                    index = 0

                    viewComp[i].text = null
                    viewComp[i].hint = "Empty Comp"
                    viewComp[i].error = null

                    /**
                     * disable
                     * */
                    viewComp[i].isEnabled = false
                } else {
                    viewComp[i].hint = "Enter Amount"
                    viewComp[i].isEnabled = true
                }

                btn.text = _fuelTypeList[index]
            }
        }
    }

    private fun compartmentsInputs() {

        viewComp.forEach {
            it.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                    if (it.isEnabled && (s.isNullOrEmpty() || s.toString().toInt() < 500)) {
                        it.error = "Should contain more than 500L"
                    } else {
                        if (it.isEnabled && (s.isNullOrEmpty() || s.toString().toInt() > 30000)) {
                            it.error = "Can't carry more than 30000L"
                        } else {
                            it.error = null
                        }
                    }
                }
            })
        }
    }


    private fun getBatchName(batches: Batches): String? {
        return if (!batches.entries.isNullOrEmpty()) {
            val entrySize = batches.entries!!.size
            batches.entries!![entrySize - 1].name
        } else {
            "****************"
        }
    }


    private fun requestPermissions() {
        Dexter.withActivity(this)
            .withPermissions(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    validateAndPost()
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {

                }
            }).check()
    }


    private fun validateAndPost() {
        /**
         * check top inputs
         * */
        var inputErrors = false

        _topInputs.forEach {
            if (it.text.toString().isNullOrEmpty()) {
                inputErrors = true

                it.error = "This field cant be blank"
            }
        }


        /**
         * check if compartments are fine
         *
         * local fuel inputs
         * */

        var pmsLocal = depotOrder?.fuel?.pms?.qty!!
        var agoLocal = depotOrder?.fuel?.ago?.qty!!
        var ikLocal = depotOrder?.fuel?.ik?.qty!!


        // from buttons
        btnComp.forEachIndexed { index, appCompatButton ->
            when (appCompatButton.text) {
                "PMS" ->
                    if (!viewComp[index].text.isNullOrEmpty()) {
                        pmsLocal -= viewComp[index].text.toString().toInt()
                    } else {
                        inputErrors = true
                        viewComp[index].error = "Field can't be empty"
                    }


                "AGO" ->
                    if (!viewComp[index].text.isNullOrEmpty()) {
                        agoLocal -= viewComp[index].text.toString().toInt()
                    } else {
                        inputErrors = true
                        viewComp[index].error = "Field can't be empty"
                    }

                "IK" ->

                    if (!viewComp[index].text.isNullOrEmpty()) {
                        ikLocal -= viewComp[index].text.toString().toInt()
                    } else {
                        inputErrors = true
                        viewComp[index].error = "Field can't be empty"
                    }
            }
        }


        // check if the local fuel is zero and no error
        if (pmsLocal == 0 && agoLocal == 0 && ikLocal == 0 && !inputErrors) {
            pushToServer()
        } else {

            progressDialog.dismiss()

            toast("Make sure you have no errors")

            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.run {
                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrate(
                            VibrationEffect.createOneShot(
                                500,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrate(500)
                    }
                } // for 500 ms
            }
        }

    }


    private fun pushToServer() {
        val compList = ArrayList<Compartment>()

        btnComp.forEachIndexed { index, appCompatButton ->

            when (val btnValue: String = appCompatButton.text.toString()) {
                "EMPTY" ->
                    compList.add(Compartment(index, null, null))

                else ->
                    compList.add(
                        Compartment(
                            index,
                            btnValue.toLowerCase(),
                            viewComp[index].text.toString().toInt()
                        )
                    )
            }
        }

        val driverName = et_driver_name.text.toString().toUpperCase()
        val driverId = et_driver_id.text.toString().toUpperCase()
        val numberPlate = et_driver_plate.text.toString().toUpperCase()

        truckDetailViewModel.updateCompartmentAndDriver(
            _user,
            depotOrder?.Id!!,
            compList,
            driverId,
            driverName,
            numberPlate
        ).observe(this, Observer {

            if (it.isSuccessful) {
                // create an image to print
                cleanPageForPrinting()
            } else {
                progressDialog.dismiss()
                Snackbar.make(layout_constraint, "An error occurred", Snackbar.LENGTH_SHORT).show()
                Timber.e(it.error()!!)

            }
        })
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_from_left, R.anim.slide_out_to_right)
    }


    var saveImageSub: Disposable? = null
    private fun cleanPageForPrinting() {
        hideButton(false)
        activityUtil.disableViews(layout_constraint)

        /**
         * Take screenshot now
         * */

        // clear to avoid leaks
        saveImageSub?.dispose()
        saveImageSub = null

        saveImageSub = imageUtil.reactiveTakeScreenShot(content_scroll)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                if (it) {
                    hideButton(true)

                    progressDialog.dismiss()


                    PrintingActivity.startPrintingActivity(
                        this,
                        depotOrder?.Id!!,
                        "1"
                    )
                } else {
                    /**
                     * An error occured
                     * */
                    progressDialog.dismiss()

                    Toast.makeText(this, "Sorry an error occurred", Toast.LENGTH_SHORT).show()
                    hideButton(true)
                }
            }
    }


    private fun hideButton(visible: Boolean) {
        if (visible) {
            btnPrint.visibility = View.VISIBLE
        } else {
            btnPrint.visibility = View.GONE
        }
    }

}

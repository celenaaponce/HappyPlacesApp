package com.example.happyplaces.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.example.happyplaces.R
import com.example.happyplaces.database.DatabaseHandler
import com.example.happyplaces.models.HappyPlaceModel
import com.example.happyplaces.utils.GetAddressFromLatLng
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener.Builder.withContext
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_add_happy_place.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class AddHappyPlaceActivity : AppCompatActivity(), View.OnClickListener {
    private val cal = Calendar.getInstance()
    private lateinit var dateSetListener: DatePickerDialog.OnDateSetListener
    private var saveImageToInternalStorage: Uri? = null
    private var mLatitude : Double = 0.0
    private var mLongitude: Double = 0.0
    private var mHappyPlaceDetails : HappyPlaceModel? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_happy_place)
        var etDate: EditText = findViewById(R.id.etDate)
        var toolbarAddPlace: Toolbar = findViewById(R.id.toolbar_add_place)
        var tvAddImage: TextView = findViewById(R.id.tvPic)
        var btnSave: Button = findViewById(R.id.btnSave)
        setSupportActionBar(toolbarAddPlace)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbarAddPlace?.setNavigationOnClickListener {
            onBackPressed()
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        if(!Places.isInitialized()){
            Places.initialize(this@AddHappyPlaceActivity, resources.getString(R.string.google_maps_api_key))
        }

        if(intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)){
            mHappyPlaceDetails = intent.getParcelableExtra(MainActivity.EXTRA_PLACE_DETAILS) as HappyPlaceModel
        }

        dateSetListener = DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateInView(etDate)

        }
        updateDateInView(etDate)

        if(mHappyPlaceDetails != null){
            supportActionBar?.title = "Edit Happy Place"
            et_title.setText(mHappyPlaceDetails!!.title)
            etDescription.setText(mHappyPlaceDetails!!.description)
            etDate.setText(mHappyPlaceDetails!!.date)
            etLocation.setText(mHappyPlaceDetails!!.location)
            mLatitude = mHappyPlaceDetails!!.latitude
            Log.e("lat", "$mLatitude")
            mLongitude = mHappyPlaceDetails!!.longitude
            Log.e("long", "$mLongitude")

            saveImageToInternalStorage = Uri.parse(mHappyPlaceDetails!!.image)
            iv_place_image.setImageURI(saveImageToInternalStorage)
            btnSave.text = "UPDATE"
        }
        etDate.setOnClickListener(this)
        tvAddImage.setOnClickListener(this)
        btnSave.setOnClickListener(this)
        etLocation.setOnClickListener(this)
        tvSelectCurrentLocation.setOnClickListener(this)
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData(){
        var mLocationRequest = LocationRequest.create().apply {
            interval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallBack,
            Looper.myLooper()!!
        )
    }


    private val mLocationCallBack = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            mLatitude = mLastLocation!!.latitude
            mLongitude = mLastLocation!!.longitude
            Log.i("Current Latitude", "$mLatitude")
            Log.i("Current Longitude", "$mLongitude")

            val addressTask =
                GetAddressFromLatLng(this@AddHappyPlaceActivity, mLatitude, mLongitude)

            addressTask.setAddressListener(object :
                GetAddressFromLatLng.AddressListener {
                override fun onAddressFound(address: String?) {
                    Log.e("Address ::", "" + address)
                    etLocation.setText(address) // Address is set to the edittext
                }

                override fun onError() {
                    Log.e("Get Address ::", "Something is wrong...")
                }
            })

            addressTask.getAddress()

        }
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.etDate -> {
                DatePickerDialog(
                    this@AddHappyPlaceActivity,
                    dateSetListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
            R.id.tvPic -> {
                val pictureDialog = AlertDialog.Builder(this)
                pictureDialog.setTitle("Select Action")
                val pictureDialogItems =
                    arrayOf("Select photo from Gallery", "Capture photo from camera")
                pictureDialog.setItems(pictureDialogItems) { dialog, which ->
                    when (which) {
                        0 -> choosePhotoFromGallery()
                        1 -> takePhotoFromCamera()
                    }
                }
                pictureDialog.show()
            }
            R.id.btnSave -> {
                when{

                    et_title.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter title", Toast.LENGTH_SHORT).show()
                    }
                    etDescription.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT).show()
                    }
                    etLocation.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter location", Toast.LENGTH_SHORT).show()
                    }
                    saveImageToInternalStorage == null -> {
                        Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show()
                    }else -> {
                        val happyPlaceModel = HappyPlaceModel(
                            if(mHappyPlaceDetails == null) 0 else mHappyPlaceDetails!!.id,
                            et_title.text.toString(),
                            saveImageToInternalStorage.toString(),
                            etDescription.text.toString(),
                            etDate.text.toString(),
                            etLocation.text.toString(),
                            mLatitude,
                            mLongitude
                        )
                    val dbHandler = DatabaseHandler(this)
                    if (mHappyPlaceDetails == null){
                        val addHappyPlace = dbHandler.addHappyPlace(happyPlaceModel)
                        if(addHappyPlace > 0){
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                    }else{
                        val updateHappyPlace = dbHandler.updateHappyPlace(happyPlaceModel)
                        if(updateHappyPlace > 0){
                            setResult(Activity.RESULT_OK)
                            finish()
                        }

                    }


                    }
                }
            }
            R.id.etLocation ->{
                try{
                    val fields = listOf(
                        Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG,
                        Place.Field.ADDRESS
                    )
                    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields).build(this@AddHappyPlaceActivity)
                    startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)


                }catch(e:Exception){
                    e.printStackTrace()
                }
            }
            R.id.tvSelectCurrentLocation -> {
                if(!isLocationEnabled()){
                    Toast.makeText(this, "Your location provider is turned off", Toast.LENGTH_SHORT).show()

                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                }else{
                    Dexter.withActivity(this).withPermissions(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ).withListener(object: MultiplePermissionsListener{
                        override fun onPermissionsChecked(report: MultiplePermissionsReport?){
                            if(report!!.areAllPermissionsGranted()){
                                requestNewLocationData()
                            }
                        }

                        override fun onPermissionRationaleShouldBeShown(
                            permissions: MutableList<PermissionRequest>?,
                            token: PermissionToken?
                        ) {
                            showRationalDialogForPermissions()
                        }
                    }).onSameThread().check()
                }
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK){
            if(requestCode == GALLERY){
                if(data != null){
                    val contentURI = data.data
                    try{
                        val selectImageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, contentURI)
                        saveImageToInternalStorage = saveImageToInternalStorage(selectImageBitmap)
                        Log.e("Saved image: ", "Path :: $saveImageToInternalStorage")
                        iv_place_image.setImageBitmap(selectImageBitmap)
                    }catch (e: IOException){
                        e.printStackTrace()
                        Toast.makeText(this@AddHappyPlaceActivity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else if(requestCode == CAMERA){
                if(data != null){
                    val thumbnail: Bitmap = data!!.extras!!.get("data") as Bitmap
                    saveImageToInternalStorage = saveImageToInternalStorage(thumbnail)
                    Log.e("Saved image: ", "Path :: $saveImageToInternalStorage")
                    iv_place_image.setImageBitmap(thumbnail)
                }
            }else if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE){
                val place: Place = Autocomplete.getPlaceFromIntent(data!!)
                etLocation.setText(place.address)
                mLatitude = place.latLng!!.latitude
                mLongitude = place.latLng!!.longitude
            }
        }
    }

    private fun takePhotoFromCamera(){
        Dexter.withActivity(this)
        .withPermissions(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                if (report!!.areAllPermissionsGranted()) {
                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(cameraIntent, CAMERA)
                } else showRationalDialogForPermissions()
            }

            override fun onPermissionRationaleShouldBeShown(
                permissions: MutableList<com.karumi.dexter.listener.PermissionRequest>,
                token: PermissionToken
            ) {
                showRationalDialogForPermissions()
                token.continuePermissionRequest()
            }
        }).withErrorListener {

            // on below line method will be called when dexter
            // throws any error while requesting permissions.
            Toast.makeText(this, it.name, Toast.LENGTH_SHORT).show()
        }.onSameThread().check()}


    private fun choosePhotoFromGallery() {
        Dexter.withActivity(this)
            .withPermissions(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        startActivityForResult(galleryIntent, GALLERY)
                    } else showRationalDialogForPermissions()
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<com.karumi.dexter.listener.PermissionRequest>,
                    token: PermissionToken
                ) {
                    showRationalDialogForPermissions()
                    token.continuePermissionRequest()
                }
            }).withErrorListener {

                // on below line method will be called when dexter
                // throws any error while requesting permissions.
                Toast.makeText(this, it.name, Toast.LENGTH_SHORT).show()
            }.onSameThread().check()
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage("It looks like you turned off permissions required.")
            .setPositiveButton("GO TO SETTINGS")
            { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }.show()
    }

    private fun updateDateInView(etDate: EditText) {
        val myFormat = "dd.MM.yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault())
        etDate.setText(sdf.format(cal.time).toString())
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri{
        val wrapper = ContextWrapper(applicationContext)
        var file = wrapper.getDir(IMAGE_DIRECTORY, Context.MODE_PRIVATE)
        file = File(file, "${UUID.randomUUID()}.jpg")

        try{
            val stream: OutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
        }catch (e: IOException){
            e.printStackTrace()
        }
        return Uri.parse(file.absolutePath)
    }

    companion object{
        private const val GALLERY = 1
        private const val CAMERA = 2
        private const val IMAGE_DIRECTORY = "HappyPlacesImages"
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 3
    }
}
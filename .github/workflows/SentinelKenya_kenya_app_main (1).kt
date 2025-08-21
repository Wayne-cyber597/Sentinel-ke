package com.sentinelkenya

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.android.gms.location.LocationServices
import java.util.UUID

// LoginActivity
class LoginActivity : AppCompatActivity() {
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var loginButton: Button
    private lateinit var anonymousButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailField = findViewById(R.id.emailField)
        passwordField = findViewById(R.id.passwordField)
        loginButton = findViewById(R.id.loginButton)
        anonymousButton = findViewById(R.id.anonymousButton)

        loginButton.setOnClickListener {
            val email = emailField.text.toString()
            val password = passwordField.text.toString()
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
        }

        anonymousButton.setOnClickListener {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
        }
    }
}

// RegisterActivity
class RegisterActivity : AppCompatActivity() {
    private lateinit var nameField: EditText
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var registerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameField = findViewById(R.id.nameField)
        emailField = findViewById(R.id.emailField)
        passwordField = findViewById(R.id.passwordField)
        registerButton = findViewById(R.id.registerButton)

        registerButton.setOnClickListener {
            val name = nameField.text.toString()
            val email = emailField.text.toString()
            val password = passwordField.text.toString()

            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    val userRef = FirebaseDatabase.getInstance().reference.child("users").child(userId!!)
                    userRef.setValue(mapOf("name" to name, "email" to email))

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
        }
    }
}

// MainActivity
class MainActivity : AppCompatActivity() {
    private lateinit var panicButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        panicButton = findViewById(R.id.panicButton)
        panicButton.setOnClickListener {
            triggerPanicMode()
        }
    }

    private fun triggerPanicMode() {
        LocationService(this).getCurrentLocation { lat, lon ->
            FirebaseUtils.sendEmergencyAlert(lat, lon)
        }
    }
}

// ReportActivity
class ReportActivity : AppCompatActivity() {
    private lateinit var uploadButton: Button
    private lateinit var submitButton: Button
    private lateinit var descriptionField: EditText
    private lateinit var selectedMediaUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        uploadButton = findViewById(R.id.uploadMedia)
        submitButton = findViewById(R.id.submitReport)
        descriptionField = findViewById(R.id.descriptionField)

        uploadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/* video/*"
            startActivityForResult(intent, 1001)
        }

        submitButton.setOnClickListener {
            LocationService(this).getCurrentLocation { lat, lon ->
                FirebaseUtils.uploadMedia(selectedMediaUri) { mediaUrl ->
                    FirebaseUtils.sendReport(descriptionField.text.toString(), mediaUrl, lat, lon)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            selectedMediaUri = data?.data!!
        }
    }
}

// LocationService
class LocationService(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fun getCurrentLocation(callback: (Double, Double) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                callback(it.latitude, it.longitude)
            }
        }
    }
}

// FirebaseUtils
object FirebaseUtils {
    fun uploadMedia(uri: Uri, callback: (String) -> Unit) {
        val mediaRef = FirebaseStorage.getInstance().reference.child("reports/${UUID.randomUUID()}")
        mediaRef.putFile(uri).addOnSuccessListener {
            mediaRef.downloadUrl.addOnSuccessListener { uri ->
                callback(uri.toString())
            }
        }
    }

    fun sendReport(description: String, mediaUrl: String, lat: Double, lon: Double) {
        val reportId = FirebaseDatabase.getInstance().reference.child("reports").push().key
        val report = mapOf(
            "description" to description,
            "mediaUrl" to mediaUrl,
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to System.currentTimeMillis()
        )
        FirebaseDatabase.getInstance().reference.child("reports").child(reportId!!).setValue(report)
    }

    fun sendEmergencyAlert(lat: Double, lon: Double) {
        val alert = mapOf(
            "type" to "panic",
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to System.currentTimeMillis()
        )
        FirebaseDatabase.getInstance().reference.child("alerts").push().setValue(alert)
    }
}

// EmergencyActivity
class EmergencyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency)

        findViewById<Button>(R.id.callPolice).setOnClickListener {
            dialNumber("999")
        }

        findViewById<Button>(R.id.callAmbulance).setOnClickListener {
            dialNumber("911")
        }
    }

    private fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$number")
        startActivity(intent)
    }
}

/*
AndroidManifest permissions (add to AndroidManifest.xml):
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.INTERNET"/>
*/
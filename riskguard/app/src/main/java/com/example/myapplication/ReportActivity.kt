package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage


import java.util.*

class ReportActivity : BaseActivity() {

    private val tag = "ReportActivity"
    private val requestPermissionCode = 123

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var anexoUri: Uri? = null


    private val selectFileLauncher = registerForActivityResult(PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            anexoUri = uri
            Toast.makeText(this, "Arquivo anexado com sucesso", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Nenhum arquivo selecionado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_report)

        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()



        if (auth.currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        firestore = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val etTitulo = findViewById<EditText>(R.id.etTitulo)
        val etDescricao = findViewById<EditText>(R.id.etDescricao)
        val rgRisco = findViewById<RadioGroup>(R.id.rgRisco)
        val btnAnexo = findViewById<Button>(R.id.btnAnexo)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarReport)

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@ReportActivity, "Você precisa sair para voltar.", Toast.LENGTH_SHORT).show()
            }
        })

        solicitarPermissoes()

        btnAnexo.setOnClickListener {
            selectFileLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
        }

        btnEnviar.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(this, "É necessário estar logado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val titulo = etTitulo.text.toString().trim()
            val descricao = etDescricao.text.toString().trim()
            val riscoId = rgRisco.checkedRadioButtonId

            if (titulo.isEmpty() || descricao.isEmpty() || riscoId == -1) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val risco = findViewById<RadioButton>(riscoId).text.toString()
            val userId = auth.currentUser!!.uid

            obterLocalizacao { location ->
                val reportData = hashMapOf(
                    "titulo" to titulo,
                    "descricao" to descricao,
                    "tipoRisco" to risco,
                    "userId" to userId,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "latitude" to (location?.latitude ?: 0.0),
                    "longitude" to (location?.longitude ?: 0.0)
                )

                if (anexoUri != null) {
                    val storageRef = FirebaseStorage.getInstance().reference
                    val fileName = UUID.randomUUID().toString()
                    val fileRef = storageRef.child("anexos/$fileName")

                    fileRef.putFile(anexoUri!!)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) throw task.exception ?: Exception("Erro no upload")
                            fileRef.downloadUrl
                        }
                        .addOnSuccessListener { uri ->
                            reportData["anexoUrl"] = uri.toString()
                            salvarNoFirestore(reportData)
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Erro ao fazer upload", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    salvarNoFirestore(reportData)
                }
            }
        }
    }

    private fun obterLocalizacao(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                requestPermissionCode
            )
            callback(null)
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location -> callback(location) }
            .addOnFailureListener {
                Log.e(tag, "Erro ao obter localização", it)
                callback(null)
            }
    }

    private fun salvarNoFirestore(data: HashMap<String, Any>) {
        firestore.collection("reports")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Report enviado com sucesso!", Toast.LENGTH_SHORT).show()
                limparCampos()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao enviar report", Toast.LENGTH_SHORT).show()
            }
    }

    private fun limparCampos() {
        findViewById<EditText>(R.id.etTitulo).setText("")
        findViewById<EditText>(R.id.etDescricao).setText("")
        findViewById<RadioGroup>(R.id.rgRisco).clearCheck()
        anexoUri = null
    }

    private fun solicitarPermissoes() {
        val permissoes = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissoes += listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            permissoes += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissoes += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (permissoes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissoes.toTypedArray(), requestPermissionCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestPermissionCode) {
            if (grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
                Toast.makeText(this, "Algumas permissões foram negadas.", Toast.LENGTH_SHORT).show()
            }
        }
    }

}


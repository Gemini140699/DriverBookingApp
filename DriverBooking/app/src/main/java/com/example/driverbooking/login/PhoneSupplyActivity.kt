package com.example.driverbooking.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.driverbooking.R
import com.example.driverbooking.databinding.ActivityPhoneSupplyBinding
import com.example.driverbooking.model.DriverInfoModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class PhoneSupplyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhoneSupplyBinding
    private lateinit var model: DriverInfoModel
    private lateinit var database: FirebaseDatabase
    private lateinit var driveInForReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_phone_supply)
        model = DriverInfoModel()
        checkPhoneNumber()
        binding.btnSignInPhone.setOnClickListener() {
            updateDriveInfo()
        }
    }

    private fun checkPhoneNumber() {
        binding.txtPhone.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // TODO Auto-generated method stub
            }

            override fun afterTextChanged(s: Editable) {
                if (s.length > 8) {
                    binding.btnSignInPhone.visibility = View.VISIBLE
                } else {
                    binding.btnSignInPhone.visibility = View.GONE
                }
            }
        })

    }

    private fun updateDriveInfo() {
        val user = FirebaseAuth.getInstance().currentUser
        database = FirebaseDatabase.getInstance()
        driveInForReference = database.getReference("driverInfo")
        if (user != null) {
            model.avatar = user.photoUrl!!.toString()
            model.displayName = user.displayName!!
            model.email = user.email!!
            model.phoneNumber =
                binding.ccp.selectedCountryCodeWithPlus.toString() + binding.txtPhone.text.toString()
            model.rating = 0.0f
            driveInForReference.child(user.uid).setValue(model).addOnCompleteListener { e ->
                finish()
            }
                .addOnFailureListener { e ->
                    Log.e("Error", "${e.message}")
                }
        }
    }
}
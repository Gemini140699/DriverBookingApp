package com.example.userbooking.ui.home


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.userbooking.R
import com.example.userbooking.databinding.ActivityHomeBinding
import com.example.userbooking.login.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*


class HomeActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityHomeBinding
    private lateinit var userAvatar: ImageView
    private lateinit var userInfoReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding =
            DataBindingUtil.setContentView<ActivityHomeBinding>(this, R.layout.activity_home)


        val navController = this.findNavController(R.id.myNavHostFragment)
        drawerLayout = binding.drawerLayout
        appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)

        NavigationUI.setupActionBarWithNavController(this, navController, drawerLayout)
        NavigationUI.setupWithNavController(binding.navView, navController)
        setupDriverInfo()
        setupNavDrawer()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = this.findNavController(R.id.myNavHostFragment)
        return NavigationUI.navigateUp(navController, drawerLayout)
    }

    private fun setupDriverInfo() {

        val headerView = binding.navView.getHeaderView(0)
        val userStar = headerView.findViewById<TextView>(R.id.user_star)
        val userName = headerView.findViewById<TextView>(R.id.user_name)
        val userPhone = headerView.findViewById<TextView>(R.id.user_phone)
        val userEmail = headerView.findViewById<TextView>(R.id.user_email)
        userAvatar = headerView.findViewById(R.id.img_avatar)
        val user = FirebaseAuth.getInstance().currentUser
        userInfoReference =
            FirebaseDatabase.getInstance().getReference("userInfo").child(user!!.uid)
        userInfoReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                //set drive info to nav header
                Glide.with(this@HomeActivity)
                    .load(dataSnapshot.child("avatar").value.toString())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(userAvatar)
                userStar.text = dataSnapshot.child("rating").value.toString()
                userName.text =
                    StringBuilder("Welcome, ").append(dataSnapshot.child("displayName").value.toString())
                userPhone.text =
                    StringBuilder("Phone: ").append(dataSnapshot.child("phoneNumber").value.toString())
                userEmail.text =
                    StringBuilder("Email: ").append(dataSnapshot.child("email").value.toString())
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Log.w("data check cancelled", "loadPost:onCancelled", databaseError.toException())
                // ...
            }
        })


    }


    private fun setupNavDrawer() {
        binding.navView.setNavigationItemSelectedListener {
            if (it.itemId == R.id.sign_out) {
                val alertDialog = AlertDialog.Builder(this@HomeActivity)
                alertDialog.setTitle("Sign out")
                    .setMessage("Do you want to sign out?")
                    .setPositiveButton("SIGN OUT") { dialog, id ->
                        FirebaseAuth.getInstance().signOut()
                        startActivity(
                            Intent(
                                this,
                                LoginActivity::class.java
                            ).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    }
                    .setNegativeButton("CANCEL") { dialog, id -> dialog.dismiss() }
                    .setCancelable(false).show()
            }
            true
        }
    }


}
package pl.igorwumk.drivetracker

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import pl.igorwumk.drivetracker.api.APIService
import pl.igorwumk.drivetracker.api.RegistrationRequest
import pl.igorwumk.drivetracker.api.RetrofitClient
import pl.igorwumk.drivetracker.api.UserResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterDialog(context: Context) : DialogFragment() {
    private lateinit var authService: APIService

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        authService = RetrofitClient.instance.create(APIService::class.java)
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_register, null)
        val etUser  = view.findViewById<EditText>(R.id.et_username)
        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPass  = view.findViewById<EditText>(R.id.et_pass)
        val etPass2 = view.findViewById<EditText>(R.id.et_pass2)

        return AlertDialog.Builder(requireContext())
            .setTitle("Register")
            .setView(view)
            .create()
            .apply {
                setOnShowListener {
                    findViewById<Button>(R.id.btn_register)!!.setOnClickListener {
                        val u = etUser.text.toString()
                        val e = etEmail.text.toString()
                        val p = etPass.text.toString()
                        val p2= etPass2.text.toString()
                        if (u.isBlank()||e.isBlank()||p.isBlank()) {
                            // simple validation
                            Toast.makeText(context,"All fields required",Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (p!=p2) {
                            etPass2.error = "Passwords must match"
                            return@setOnClickListener
                        }
                        // show ProgressDialog
                        val progress = ProgressDialog(context).apply {
                            setMessage("Logging in...")
                            setCancelable(false)
                            show()
                        }
                        authService.register( RegistrationRequest(u,e,p) ).enqueue(object: Callback<UserResponse> {
                                override fun onResponse(call: Call<UserResponse>, resp: Response<UserResponse>) {
                                    progress.dismiss()
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, "Registered OK", Toast.LENGTH_SHORT).show()
                                        dismiss()
                                        LoginDialog().show(parentFragmentManager,"LoginDialog")
                                    } else {
                                        showError("Register failed: ${resp.code()}")
                                    }
                                }

                                override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                                    progress.dismiss()
                                    showError("Network error: ${t.message}")
                                }
                            })
                    }
                }
            }
    }

    private fun showError(msg: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}
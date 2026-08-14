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
            .setTitle(R.string.title_register)
            .setView(view)
            .create()
            .apply {
                setOnShowListener {
                    findViewById<Button>(R.id.btn_register)!!.setOnClickListener {
                        val u = etUser.text.toString()
                        val e = etEmail.text.toString()
                        val p = etPass.text.toString()
                        val p2= etPass2.text.toString()
                        if (u.isBlank() || e.isBlank() || p.isBlank()) {
                            // simple validation
                            Toast.makeText(context, R.string.error_all_fields_required, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (p!=p2) {
                            etPass2.error = getString(R.string.error_passwords_must_match)
                            return@setOnClickListener
                        }
                        // show ProgressDialog
                        val progress = ProgressDialog(context).apply {
                            setMessage(getString(R.string.account_registering))
                            setCancelable(false)
                            show()
                        }
                        authService.register( RegistrationRequest(u,e,p) ).enqueue(object: Callback<UserResponse> {
                                override fun onResponse(call: Call<UserResponse>, resp: Response<UserResponse>) {
                                    progress.dismiss()
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, R.string.account_registered, Toast.LENGTH_SHORT).show()
                                        dismiss()
                                        LoginDialog().show(parentFragmentManager,"LoginDialog")
                                    } else {
                                        showError(getString(R.string.account_register_failed_verbose, resp.code(), resp.message()))
                                    }
                                }

                                override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                                    progress.dismiss()
                                    showError(getString(R.string.network_connection_failed, t.localizedMessage))
                                }
                            })
                    }
                }
            }
    }

    private fun showError(msg: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_error)
            .setMessage(msg)
            .setPositiveButton(R.string.modal_ok, null)
            .show()
    }
}
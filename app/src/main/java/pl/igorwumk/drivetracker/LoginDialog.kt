package pl.igorwumk.drivetracker

import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import pl.igorwumk.drivetracker.activity.EncryptedPrefs
import pl.igorwumk.drivetracker.activity.MainActivity
import pl.igorwumk.drivetracker.api.APIService
import pl.igorwumk.drivetracker.api.LoginRequest
import pl.igorwumk.drivetracker.api.LoginResponse
import pl.igorwumk.drivetracker.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginDialog() : DialogFragment() {
    private lateinit var authService: APIService
    private val prefs by lazy { EncryptedPrefs.get(requireContext()) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        authService = RetrofitClient.instance.create(APIService::class.java)
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_login, null)
        val etUser = view.findViewById<EditText>(R.id.et_username)
        val etPass = view.findViewById<EditText>(R.id.et_password)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_login)
            .setView(view)
            .create()
            .apply {
                setOnShowListener {
                    findViewById<Button>(R.id.btn_login)!!.setOnClickListener {
                        val user = etUser.text.toString()
                        val pass = etPass.text.toString()
                        if (user.isBlank() || pass.isBlank()) {
                            etUser.error = if (user.isBlank()) getString(R.string.error_field_required) else null
                            etPass.error = if (pass.isBlank()) getString(R.string.error_field_required) else null
                            return@setOnClickListener
                        }
                        // show ProgressDialog
                        val progress = ProgressDialog(context).apply {
                            setMessage(getString(R.string.account_logging_in))
                            setCancelable(false)
                            show()
                        }
                        // Call login API
                        authService.login(LoginRequest(user, pass)).enqueue(object:
                            Callback<LoginResponse> {
                            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                                progress.dismiss()
                                if (response.isSuccessful) {
                                    val body = response.body()!!
                                    // Save credentials
                                    prefs.saveCredentials(body.username, body.token)
                                    Toast.makeText(context, getString(R.string.account_logged_in_as, body.username), Toast.LENGTH_SHORT).show()
                                    dismiss()
                                    // Update UI and initiate synchronization
                                    (activity as? MainActivity)?.updateLoginHeader()
                                    (activity as? MainActivity)?.doSyncWithServer(body.token)
                                } else {
                                    showError(getString(R.string.account_login_failed_verbose, response.code(), response.message()))
                                }
                            }

                            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                                progress.dismiss()
                                showError(getString(R.string.network_connection_failed, t.localizedMessage))
                            }
                        })
                    }
                    findViewById<Button>(R.id.btn_register)?.setOnClickListener {
                        dismiss()
                        RegisterDialog(context).show(parentFragmentManager, "RegisterDialog")
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
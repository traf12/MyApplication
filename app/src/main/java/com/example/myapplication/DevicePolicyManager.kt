import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.myapplication.R

class MainActivity : Activity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private val REQUEST_CODE_DEVICE_ADMIN = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
    }

    // Метод для блокировки экрана
    fun lockScreenUsingDevicePolicy() {
        if (devicePolicyManager.isAdminActive(componentName)) {
            devicePolicyManager.lockNow()
        } else {
            // Запрашиваем права администратора
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
        }
    }

    // Обработка результата запроса прав администратора
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_DEVICE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                // Администратор устройства был активирован, теперь можно блокировать экран
                lockScreenUsingDevicePolicy()
            } else {
                // Администратор устройства не был активирован
                Toast.makeText(this, "Не удалось активировать администратора устройства", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

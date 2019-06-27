import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.net.HttpCookie

fun main() {
    runBlocking { runApp() }
}

suspend fun runApp() {
    coroutineScope {
        System.setProperty(
            "webdriver.chrome.driver",
            File("chromedriver_win32\\chromedriver_74.0.3729.6.exe").absolutePath
        )

        val opts = ChromeOptions().also {
            //        it.addArguments("--headless")
        }

        repeat(5) {
            launch {
                val driver = ChromeDriver(opts)
                val email = async {
                    val resp = Fuel.get("http://api.guerrillamail.com/ajax.php", listOf("f" to "get_email_address"))
                        .header(
                            "User-Agent" to "PostmanRuntime/7.15.0"
                        ).responseObject(TempEmailAddress)/*.third.component1()*/

                    val sessionId =
                        resp.second.headers["Set-Cookie"].flatMap { HttpCookie.parse(it) }
                            .find { it.name == "PHPSESSID" }
                            ?.value
                    resp.third.get()
                }

                registerOnBattleNet(driver, email)
//            driver.quit()
            }
        }
    }
}

data class TempEmailAddress(
    @SerializedName("email_addr")
    val emailAddr: String,
    @SerializedName("email_timestamp")
    val emailTimestamp: Long,
    @SerializedName("alias")
    val alias: String,
    @SerializedName("sid_token")
    val sidToken: String
) {
    companion object : ResponseDeserializable<TempEmailAddress> {
        private val gson = Gson()
        override fun deserialize(content: String): TempEmailAddress = gson.fromJson(content)
    }
}

import com.github.javafaker.Faker
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import edu.vt.middleware.password.DigitCharacterRule
import edu.vt.middleware.password.PasswordGenerator
import edu.vt.middleware.password.UppercaseCharacterRule
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import org.openqa.selenium.chrome.ChromeDriver
import java.io.File
import java.net.URL
import java.util.*
import kotlin.random.Random

suspend fun registerOnBattleNet(
    driver: ChromeDriver,
    email: Deferred<TempEmailAddress>,
    password: String = BattleNetPasswordGenerator.generatePassword()
) {
    val name = Faker.instance().name()

    driver.get("https://eu.battle.net/account/creation/ru/")

    driver.findElementById("firstName").sendKeys(name.firstName())
    driver.findElementById("lastName").sendKeys(name.lastName())
    driver.findElementById("dobDay").sendKeys(Random.nextInt(1, 25).toString())
    driver.findElementById("dobYear").sendKeys(Random.nextInt(1970, 2000).toString())
    driver.findElementById("emailAddress").sendKeys(email.await().emailAddr)
    driver.findElementById("password").sendKeys(password)
    driver.findElementById("answer1").sendKeys("1")
//*[@id="account-creation"]/fieldset[5]/div/div/label
    driver.findElementByXPath("agreedToPrivacyPolicy")

    driver.findElementByCssSelector("#select-box-dobMonth > span.current > span").click()
    driver.executeScript("document.getElementById('dobMonth').value = 1")
    driver.executeScript("document.getElementById('question1').value = 19")

    val captcha = driver.findElementsById("security-image")
    if (captcha.size != 0) {
        val captUrl = captcha.first().getAttribute("src")
        val captBytes = URL(captUrl).readBytes()
        val base64Image = Base64.getEncoder().encodeToString(captBytes)
        driver.findElementById("captchaInput").sendKeys(solveCaptcha(base64Image))
    }

    driver.findElementById("creation-submit-button").click()

    writeUserCredentials(email.await().emailAddr, password)
}

fun writeUserCredentials(userMail: String, userPass: String) {
    File("$userMail.json").writeText(
        jsonObject(
            "email" to userMail,
            "password" to userPass
        ).toString()
    )
}

object BattleNetPasswordGenerator {
    private val generator = PasswordGenerator()
    private val rules = listOf(UppercaseCharacterRule(1), DigitCharacterRule(1))

    fun generatePassword() = generator.generatePassword(Random.nextInt(8, 16), rules)!!
}

suspend fun solveCaptcha(base64Image: String): String {
    val taskId = Fuel.post("https://api.anti-captcha.com/createTask")
        .body(
            jsonObject(
                "clientKey" to "ac3831076d42b6472cf75df9524997c3",
                "task" to jsonObject(
                    "type" to "ImageToTextTask",
                    "body" to base64Image
                )
            ).toString()
        ).responseObject(SimpleTree).third.get().obj["taskId"].int

    var taskResult: ResponseResultOf<JsonElement>

    do {
        delay(5000)
        taskResult = Fuel.post("https://api.anti-captcha.com/getTaskResult")
            .body(
                jsonObject(
                    "clientKey" to "ac3831076d42b6472cf75df9524997c3",
                    "taskId" to taskId
                ).toString()
            ).responseObject(SimpleTree)
    } while (taskResult.third.get().obj["status"].string != "ready")

    return taskResult.third.get().obj["solution"].obj["text"].string
}

object SimpleTree : ResponseDeserializable<JsonElement> {
    private val parser = JsonParser()
    override fun deserialize(content: String): JsonElement = parser.parse(content)
}

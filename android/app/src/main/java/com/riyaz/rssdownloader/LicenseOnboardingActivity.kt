package com.riyaz.rssdownloader

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LicenseOnboardingActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("rss-downloader-license", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var root: FrameLayout
    private lateinit var card: LinearLayout
    private lateinit var status: TextView
    private lateinit var name: EditText
    private lateinit var email: EditText
    private var page = 0
    private var animator: ValueAnimator? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (prefs.getBoolean("registered", false)) {
            if (prefs.getBoolean("onboarding_complete", false)) openApp() else showOnboarding()
            return
        }
        showRegistration()
    }

    override fun onDestroy() { animator?.cancel(); executor.shutdownNow(); super.onDestroy() }

    private fun background() {
        root = FrameLayout(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(25,35,82), Color.rgb(5,9,22), Color.rgb(68,35,95)))
        val mark = TextView(this).apply { text="RSS"; textSize=96f; gravity=Gravity.CENTER; setTextColor(Color.argb(35,255,255,255)) }
        root.addView(mark, FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        animator = ValueAnimator.ofFloat(-1f,1f).apply {
            duration=6500; repeatCount=ValueAnimator.INFINITE; repeatMode=ValueAnimator.REVERSE
            addUpdateListener { v -> val f=v.animatedValue as Float; mark.translationX=resources.displayMetrics.widthPixels*.16f*f; mark.translationY=resources.displayMetrics.heightPixels*.05f*f }
            start()
        }
    }

    private fun showRegistration() {
        background()
        card=glass(); card.addView(logo(),LinearLayout.LayoutParams(-1,80.dp()))
        card.addView(t("Welcome to RSS Downloader",25,true))
        card.addView(t("Register once to activate this installation.",13,false))
        name=input("Customer name"); email=input("Email address")
        card.addView(name,LinearLayout.LayoutParams(-1,52.dp()).apply{bottomMargin=10.dp()})
        card.addView(email,LinearLayout.LayoutParams(-1,52.dp()).apply{bottomMargin=10.dp()})
        card.addView(t("Internet connection is required for first registration.",11,false))
        status=t("",12,false); card.addView(status)
        val b=button("Register & Continue"); card.addView(b,LinearLayout.LayoutParams(-1,52.dp()).apply{topMargin=12.dp()})
        b.setOnClickListener{register()}; attach()
    }

    private fun register() {
        val n=name.text.toString().trim(); val e=email.text.toString().trim()
        if(n.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()){status.text="Enter a name and valid email.";return}
        status.text="Connecting to RSS License Server…"; name.isEnabled=false; email.isEnabled=false
        executor.execute {
            val result=runCatching {
                val c=URL(BuildConfig.RSS_HOST_BASE_URL.trimEnd('/')+"/api/v1/license/register").openConnection() as HttpURLConnection
                c.requestMethod="POST"; c.connectTimeout=12000; c.readTimeout=15000; c.doOutput=true
                c.setRequestProperty("content-type","application/json")
                val body=JSONObject().apply{put("email",e);put("display_name",n);put("project_key","rss-downloader");put("device_id",deviceId())}.toString()
                c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
                val code=c.responseCode; val txt=(if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
                if(code !in 200..299) error(JSONObject(txt).optString("error","Registration failed"))
                JSONObject(txt)
            }
            runOnUiThread {
                result.onSuccess { r -> prefs.edit().putBoolean("registered",true).putString("customer_id",r.optString("customer_id")).putString("app_key",r.optString("app_key")).putString("display_name",n).putString("email",e).apply(); showOnboarding() }
                    .onFailure { status.text=it.message ?: "Registration failed. Check Internet and retry."; name.isEnabled=true; email.isEnabled=true }
            }
        }
    }

    private fun showOnboarding() { background(); page=0; showPage() }

    private fun showPage() {
        card=glass(); card.addView(logo(),LinearLayout.LayoutParams(-1,72.dp()))
        val customer=prefs.getString("display_name","Customer") ?: "Customer"
        if(page==0) {
            card.addView(t("Congratulations 👏🎉",27,true))
            card.addView(t("Welcome "+customer+"!",23,true))
            card.addView(t("Your RSS Downloader installation is registered with RSS License Server.",14,false))
        } else {
            val titles=arrayOf("Social downloads","Tamil Movies","Tamil Dubbed Movies","Downloads & control")
            val desc=arrayOf("Paste a supported link and let RSS Core analyze authorized media options.","Search Tamil movies and choose an authorized quality.","Search Tamil dubbed movies and choose the available authorized format.","Track download progress. RSS Downloader stays quiet when idle.")
            card.addView(t(titles[page-1],25,true)); card.addView(t(desc[page-1],14,false))
        }
        card.addView(t((page+1).toString()+" / 5",11,false))
        val b=button(if(page<4)"Next  →" else "Get Started")
        card.addView(b,LinearLayout.LayoutParams(-1,52.dp()).apply{topMargin=14.dp()})
        b.setOnClickListener{if(page<4){page++;showPage()}else{prefs.edit().putBoolean("onboarding_complete",true).apply();openApp()}}
        attach()
    }

    private fun openApp(){startActivity(android.content.Intent(this,MainActivity::class.java));finish()}
    private fun deviceId()=Settings.Secure.getString(contentResolver,Settings.Secure.ANDROID_ID).orEmpty().ifBlank{Build.MANUFACTURER+"-"+Build.MODEL+"-"+Build.VERSION.SDK_INT}
    private fun attach(){root.removeView(card);root.addView(card,FrameLayout.LayoutParams(-1,-2).apply{gravity=Gravity.CENTER;setMargins(18.dp(),18.dp(),18.dp(),18.dp())})}
    private fun glass()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24.dp(),24.dp(),24.dp(),24.dp());background=GradientDrawable().apply{setColor(Color.argb(190,18,24,48));cornerRadius=28.dp().toFloat();setStroke(1.dp(),Color.argb(85,255,255,255))};elevation=10.dp().toFloat()}
    private fun logo()=ImageView(this).apply{setImageResource(R.drawable.rss_downloader_logo);scaleType=ImageView.ScaleType.CENTER_INSIDE;contentDescription="RSS Downloader"}
    private fun input(h:String)=EditText(this).apply{hint=h;setHintTextColor(Color.rgb(145,155,180));setTextColor(Color.WHITE);setSingleLine();setPadding(16.dp(),0,16.dp(),0);background=GradientDrawable().apply{setColor(Color.argb(100,255,255,255));cornerRadius=16.dp().toFloat()}}
    private fun button(s:String)=TextView(this).apply{text=s;textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=android.graphics.Typeface.DEFAULT_BOLD;background=GradientDrawable().apply{setColor(Color.rgb(91,108,240));cornerRadius=16.dp().toFloat()}}
    private fun t(s:String,z:Int,b:Boolean)=TextView(this).apply{text=s;textSize=z.toFloat();setTextColor(Color.WHITE);typeface=if(b)android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT}
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
}

package com.riyaz.rssdownloader

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.text.Editable
import android.text.TextWatcher
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
    private lateinit var registerButton: TextView

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
            intArrayOf(Color.rgb(10,18,42), Color.rgb(18,8,38), Color.rgb(5,24,34)))
        val particles = ParticleBackground(this)
        root.addView(particles, FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        animator = ValueAnimator.ofFloat(0f,1f).apply {
            duration=9000; repeatCount=ValueAnimator.INFINITE
            addUpdateListener { particles.progress = animatedValue as Float; particles.invalidate() }
            start()
        }
    }

    private class ParticleBackground(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var progress = 0f
        private val points = Array(18) { i -> floatArrayOf(
            (0.06f + ((i * 37) % 88) / 100f),
            (0.08f + ((i * 61) % 84) / 100f),
            2.5f + (i % 4)
        ) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            paint.color = Color.argb(34, 210, 230, 255)
            val w=width.toFloat(); val h=height.toFloat()
            val drift = (kotlin.math.sin(progress * Math.PI * 2) * 18f).toFloat()
            for (i in 0 until 9) {
                val y = h * (0.10f + i * 0.105f) + drift * (if (i % 2 == 0) 1 else -1)
                val path=Path()
                path.moveTo(-40f,y)
                path.cubicTo(w*.25f,y-22f,w*.68f,y+22f,w+40f,y-8f)
                canvas.drawPath(path,paint)
            }
            paint.style = Paint.Style.FILL
            for (p in points) {
                val x=w*p[0] + drift*(p[1]-0.5f)
                val y=h*p[1] - drift*(p[0]-0.5f)
                paint.color=Color.argb(42,220,240,255)
                canvas.drawCircle(x,y,p[2],paint)
            }
        }
    }

    private fun showRegistration() {
        background()
        card=glass(); card.addView(logo(),LinearLayout.LayoutParams(-1,80.dp()))
        card.addView(t("Welcome to RSS Downloader",25,true))
        name=input("Customer name"); email=input("Email address")
        card.addView(name,LinearLayout.LayoutParams(-1,52.dp()).apply{bottomMargin=10.dp()})
        card.addView(email,LinearLayout.LayoutParams(-1,52.dp()).apply{bottomMargin=10.dp()})
        status=t("",12,false); card.addView(status)
        registerButton=button("Register & Continue")
        registerButton.visibility=View.GONE
        card.addView(registerButton,LinearLayout.LayoutParams(-1,52.dp()).apply{topMargin=12.dp()})
        registerButton.setOnClickListener{register()}
        val watcher=object: TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){ updateRegisterButton() }
            override fun afterTextChanged(s:Editable?){}
        }
        name.addTextChangedListener(watcher); email.addTextChangedListener(watcher)
        attach()
        updateRegisterButton()
    }

    private fun register() {
        val n=name.text.toString().trim(); val e=email.text.toString().trim()
        if(n.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()){status.text="Enter a name and valid email.";return}
        registerButton.visibility=View.GONE
        status.text="Registering…"; name.isEnabled=false; email.isEnabled=false
        executor.execute {
            val result=runCatching {
                val c=URL(BuildConfig.RSS_HOST_BASE_URL.trimEnd('/')+"/api/v1/license/register").openConnection() as HttpURLConnection
                c.requestMethod="POST"; c.connectTimeout=12000; c.readTimeout=15000; c.doOutput=true
                c.setRequestProperty("content-type","application/json")
                val body=JSONObject().apply{put("email",e);put("display_name",n);put("project_key","rss-downloader");put("device_id",deviceId())}.toString()
                c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
                val code=c.responseCode; val txt=(if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
                if(code !in 200..299) {
                    val serverError=runCatching{JSONObject(txt).optString("error").ifBlank{JSONObject(txt).optString("message")}}.getOrNull().orEmpty()
                    val message=when(code) {
                        404 -> if(serverError.isNotBlank()) serverError else "RSS Core registration service was not found."
                        408 -> "RSS Core registration timed out."
                        429 -> "Too many registration attempts. Please try again shortly."
                        else -> serverError.ifBlank{"Registration failed (HTTP $code)."}
                    }
                    error(message)
                }
                JSONObject(txt)
            }
            runOnUiThread {
                result.onSuccess { r -> prefs.edit().putBoolean("registered",true).putString("customer_id",r.optString("customer_id")).putString("app_key",r.optString("app_key")).putString("display_name",n).putString("email",e).apply(); showOnboarding() }
                    .onFailure {
                        status.text=it.message ?: "Registration failed. Please try again."
                        name.isEnabled=true; email.isEnabled=true
                        updateRegisterButton()
                    }
            }
        }
    }

    private fun updateRegisterButton() {
        if (!::registerButton.isInitialized) return
        val valid=name.text.toString().trim().isNotBlank() &&
            android.util.Patterns.EMAIL_ADDRESS.matcher(email.text.toString().trim()).matches()
        registerButton.visibility=if(valid) View.VISIBLE else View.GONE
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

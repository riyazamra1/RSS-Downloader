package com.riyaz.rssdownloader

import android.animation.ValueAnimator
import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LicenseOnboardingActivity : AppCompatActivity() {
    companion object {
        private const val TEMP_SKIP_REGISTRATION = false
    }

    private val prefs by lazy { getSharedPreferences("rss-downloader-license", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var root: FrameLayout
    private lateinit var card: LinearLayout
    private lateinit var status: TextView
    private lateinit var name: EditText
    private lateinit var email: EditText
    private var page = 0
    private var animator: ValueAnimator? = null
    private var featureAnimator: ObjectAnimator? = null
    private lateinit var registerButton: TextView
    private lateinit var termsCheck: CheckBox
    private val verificationHandler = Handler(Looper.getMainLooper())
    private var verificationExpiry = 0L
    private lateinit var verificationCountdown: TextView
    private lateinit var verificationCheck: TextView
    private lateinit var verificationResend: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (TEMP_SKIP_REGISTRATION) {
            if (!prefs.getBoolean("registered", false)) {
                prefs.edit()
                    .putBoolean("registered", true)
                    .putBoolean("onboarding_complete", true)
                    .putString("display_name", "Test User")
                    .putString("email", "")
                    .putString("plan", "free")
                    .putString("license_status", "temporary-test")
                    .apply()
            }
            openApp()
            return
        }
        if (prefs.getBoolean("registered", false)) {
            if (prefs.getBoolean("email_verified", false)) {
                if (prefs.getBoolean("onboarding_complete", false)) openApp() else showOnboarding()
            } else {
                showVerification()
                checkVerification()
            }
            return
        }
        showRegistration()
    }

    override fun onDestroy() { animator?.cancel(); featureAnimator?.cancel(); verificationHandler.removeCallbacksAndMessages(null); executor.shutdownNow(); super.onDestroy() }

    private fun background() {
        root = FrameLayout(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(7,7,7), Color.rgb(16,13,7), Color.rgb(7,7,7)))
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
            paint.color = Color.argb(24,212,175,55)
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
                paint.color=Color.argb(38,212,175,55)
                canvas.drawCircle(x,y,p[2],paint)
            }
        }
    }

    private fun showRegistration() {
        background()
        card=glass()
        card.addView(logo(),LinearLayout.LayoutParams(-1,80.dp()))
        card.addView(t("Create your RSS Downloader account",25,true))
        card.addView(t("One RSS account for app access, verification and multi-device sync.",13,false).apply{setTextColor(Color.rgb(190,190,190));setPadding(0,6.dp(),0,16.dp())})
        name=input("Full name")
        email=input("Email address")
        card.addView(name,LinearLayout.LayoutParams(-1,52.dp()).apply{bottomMargin=10.dp()})
        card.addView(email,LinearLayout.LayoutParams(-1,52.dp()).apply{bottomMargin=10.dp()})
        termsCheck=CheckBox(this).apply{
            text="I agree to the RSS Terms & Conditions and Privacy Policy"
            textSize=12f
            setTextColor(Color.rgb(220,220,220))
            buttonTintList=android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.rgb(212,175,55), Color.rgb(150,150,150))
            )
            setPadding(0,2.dp(),0,2.dp())
            setOnCheckedChangeListener{_,_->updateRegisterButton()}
        }
        card.addView(termsCheck,LinearLayout.LayoutParams(-1,48.dp()))
        status=t("Your email will receive a verification link after registration.",12,false)
        card.addView(status,LinearLayout.LayoutParams(-1,-2).apply{topMargin=4.dp()})
        registerButton=button("Create Account")
        registerButton.visibility=View.GONE
        card.addView(registerButton,LinearLayout.LayoutParams(-1,52.dp()).apply{topMargin=12.dp()})
        registerButton.setOnClickListener{register()}
        val watcher=object: TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){ updateRegisterButton() }
            override fun afterTextChanged(s:Editable?){}
        }
        name.addTextChangedListener(watcher)
        email.addTextChangedListener(watcher)
        attach()
        updateRegisterButton()
    }

    private fun register() {
        val n=name.text.toString().trim(); val e=email.text.toString().trim()
        if(n.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()){status.text="Enter a name and valid email.";return}
        if(!termsCheck.isChecked){
            status.text="Please accept the RSS Terms & Conditions and Privacy Policy."
            return
        }
        registerButton.visibility=View.GONE
        status.text="Creating your RSS account…"
        name.isEnabled=false; email.isEnabled=false; termsCheck.isEnabled=false
        executor.execute {
            val result=runCatching {
                val body=JSONObject().apply{put("email",e);put("display_name",n);put("project_key","rss-downloader");put("device_id",deviceId())}.toString()
                val base=BuildConfig.RSS_HOST_BASE_URL.trimEnd('/')
                val endpoints=arrayOf("$base/api/v1/license/register","$base/v1/license/register")
                var lastCode=0
                var lastText=""
                var registeredJson: JSONObject?=null
                for (endpoint in endpoints) {
                    val attempt = runCatching {
                        val c=URL(endpoint).openConnection() as HttpURLConnection
                        try {
                            c.requestMethod="POST"; c.connectTimeout=12000; c.readTimeout=15000; c.doOutput=true
                            c.instanceFollowRedirects=true
                            c.useCaches=false
                            c.setRequestProperty("Connection","close")
                            c.setRequestProperty("Accept-Encoding","identity")
                            c.setRequestProperty("content-type","application/json")
                            c.setRequestProperty("Accept","application/json")
                            c.setRequestProperty("X-RSS-App-Id","rss-downloader")
                            c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
                            val code=c.responseCode
                            val stream=if(code in 200..299) c.inputStream else c.errorStream
                            val text=stream?.bufferedReader()?.use{it.readText()}.orEmpty()
                            Triple(code,text,null as String?)
                        } catch (io: java.io.IOException) {
                            Triple(0,"",io.message ?: "Network connection closed unexpectedly")
                        } finally {
                            c.disconnect()
                        }
                    }.getOrElse { Triple(0,"",it.message ?: "Network connection failed") }

                    lastCode=attempt.first
                    lastText=attempt.second
                    if(lastCode in 200..299) {
                        registeredJson=JSONObject(lastText)
                        break
                    }
                    if(lastCode != 404) break
                }
                if (registeredJson == null) {
                    val serverError=runCatching{JSONObject(lastText).optString("error").ifBlank{JSONObject(lastText).optString("message")}}.getOrNull().orEmpty()
                    val message=when(lastCode) {
                        404 -> if(serverError.isNotBlank()) serverError else "RSS Core registration endpoint returned 404. RSS Core production deployment is missing the registration route."
                        408 -> "RSS Core registration timed out."
                        429 -> "Too many registration attempts. Please try again shortly."
                        502 -> serverError.ifBlank{"RSS Core could not send the verification email. Please try again shortly."}
                        503 -> serverError.ifBlank{"RSS Core email verification service is not configured. Registration cannot be completed until email verification is available."}
                        0 -> "Connection to RSS Core was interrupted. Please check your internet connection and try again."
                        else -> serverError.ifBlank{"Registration failed (HTTP $lastCode)."}
                    }
                    error(message)
                }
                registeredJson!!
            }
            runOnUiThread {
                result.onSuccess { r ->
                    val verified = r.optBoolean("email_verified", false)
                    val expiry = r.optLong("verification_expires_at", 0L)
                    prefs.edit().putBoolean("registered",true).putBoolean("email_verified",verified)
                        .putLong("verification_expires_at",expiry)
                        .putString("customer_id",r.optString("customer_id"))
                        .putString("app_key",r.optString("app_key"))
                        .putString("display_name",n).putString("email",e)
                        .putString("plan",r.optJSONObject("license")?.optString("plan").orEmpty())
                        .putString("license_status",r.optJSONObject("license")?.optString("status").orEmpty()).apply()
                    if (verified) showOnboarding() else { showVerification(); updateVerificationCountdown() }
                }
                    .onFailure {
                        status.text=it.message ?: "Registration failed. Please try again."
                        name.isEnabled=true; email.isEnabled=true; termsCheck.isEnabled=true
                        updateRegisterButton()
                    }
            }
        }
    }

    private fun updateRegisterButton() {
        if (!::registerButton.isInitialized) return
        val valid=name.text.toString().trim().isNotBlank() &&
            android.util.Patterns.EMAIL_ADDRESS.matcher(email.text.toString().trim()).matches() &&
            ::termsCheck.isInitialized && termsCheck.isChecked
        registerButton.visibility=if(valid) View.VISIBLE else View.GONE
        registerButton.alpha=if(valid) 1f else 0.45f
    }

    private fun showVerification() {
        background()
        card=glass()
        card.addView(logo(),LinearLayout.LayoutParams(-1,80.dp()))
        card.addView(t("Verify your email",26,true))
        val e=prefs.getString("email","") ?: ""
        card.addView(t("We sent a verification link to $e.",14,false))
        card.addView(t("Open the email and tap Verify Email. Then return here and check your status.",13,false))
        verificationCountdown=t("Verification link valid for 24 hours",18,true)
        card.addView(verificationCountdown,LinearLayout.LayoutParams(-1,44.dp()).apply{topMargin=14.dp()})
        status=t("Email Verification Pending",13,true); card.addView(status)
        verificationCheck=button("Check Verification Status")
        card.addView(verificationCheck,LinearLayout.LayoutParams(-1,52.dp()).apply{topMargin=14.dp()})
        verificationCheck.setOnClickListener{checkVerification()}
        verificationResend=TextView(this).apply{text="Resend verification email";textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.rgb(212,175,55));isClickable=true}
        card.addView(verificationResend,LinearLayout.LayoutParams(-1,48.dp()).apply{topMargin=4.dp()})
        verificationResend.setOnClickListener{resendVerification()}
        attach()
        updateVerificationCountdown()
    }

    private fun updateVerificationCountdown() {
        if(!::verificationCountdown.isInitialized)return
        verificationExpiry=prefs.getLong("verification_expires_at",0L)
        verificationHandler.removeCallbacksAndMessages(null)
        val tick=object:Runnable{
            override fun run(){
                val left=(verificationExpiry-System.currentTimeMillis()).coerceAtLeast(0L)
                val h=left/3600000; val m=(left%3600000)/60000; val s=(left%60000)/1000
                verificationCountdown.text=if(left>0)String.format(java.util.Locale.US,"%02d:%02d:%02d remaining",h,m,s) else "Verification link expired"
                if(left>0)verificationHandler.postDelayed(this,1000)
            }
        }
        verificationHandler.post(tick)
    }

    private fun checkVerification() {
        val email=prefs.getString("email","").orEmpty()
        if(email.isBlank())return
        verificationCheck.visibility=View.GONE
        status.text="Checking verification…"
        executor.execute {
            val result=runCatching{
                val base=BuildConfig.RSS_HOST_BASE_URL.trimEnd('/')
                val endpoint="$base/api/v1/license/verification-status?email="+java.net.URLEncoder.encode(email,"UTF-8")+"&project_key=rss-downloader"
                val c=URL(endpoint).openConnection() as HttpURLConnection
                try{c.requestMethod="GET";c.connectTimeout=12000;c.readTimeout=15000;c.useCaches=false;c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-RSS-App-Id","rss-downloader");val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val body=stream?.bufferedReader()?.use{it.readText()}.orEmpty();if(code !in 200..299)error(JSONObject(body).optString("error").ifBlank{"Verification status unavailable"});JSONObject(body)}finally{c.disconnect()}
            }
            runOnUiThread{
                result.onSuccess{r->
                    val verified=r.optBoolean("email_verified",false)
                    if(verified){prefs.edit().putBoolean("email_verified",true).putLong("verification_expires_at",0L).apply();status.text="Email verified ✓";showOnboarding()}
                    else{prefs.edit().putLong("verification_expires_at",r.optLong("verification_expires_at",prefs.getLong("verification_expires_at",0L))).apply();status.text=if(r.optBoolean("verification_expired",false))"Verification link expired — resend a new email." else "Email Verification Pending";verificationCheck.visibility=View.VISIBLE;updateVerificationCountdown()}
                }.onFailure{status.text=it.message ?: "Verification status unavailable";verificationCheck.visibility=View.VISIBLE}
            }
        }
    }

    private fun resendVerification() {
        val email=prefs.getString("email","").orEmpty(); val display=prefs.getString("display_name","").orEmpty()
        if(email.isBlank())return
        verificationResend.visibility=View.GONE;status.text="Sending verification email…"
        executor.execute{
            val result=runCatching{
                val body=JSONObject().apply{put("email",email);put("display_name",display);put("project_key","rss-downloader");put("device_id",deviceId())}.toString()
                val c=URL(BuildConfig.RSS_HOST_BASE_URL.trimEnd('/')+"/api/v1/license/resend-verification").openConnection() as HttpURLConnection
                try{c.requestMethod="POST";c.connectTimeout=12000;c.readTimeout=15000;c.doOutput=true;c.useCaches=false;c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-RSS-App-Id","rss-downloader");c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))};val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val text=stream?.bufferedReader()?.use{it.readText()}.orEmpty();if(code !in 200..299)error(JSONObject(text).optString("error").ifBlank{"Unable to resend verification email"});JSONObject(text)}finally{c.disconnect()}
            }
            runOnUiThread{result.onSuccess{val expiry=it.optLong("verification_expires_at",0L);if(expiry>0L)prefs.edit().putLong("verification_expires_at",expiry).apply();status.text="Verification email sent.";verificationResend.visibility=View.VISIBLE;updateVerificationCountdown()}.onFailure{status.text=it.message ?: "Unable to resend verification email";verificationResend.visibility=View.VISIBLE}}
        }
    }

    private fun showOnboarding() { background(); page=0; showPage() }

    private fun showPage() {
        card=glass(); card.addView(logo(),LinearLayout.LayoutParams(-1,72.dp()))
        val featureIcon = TextView(this).apply { text = if (page == 0) "✓" else arrayOf("↗","🎬","▣","↓")[page-1]; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = GradientDrawable().apply { setColor(Color.argb(35,212,175,55)); cornerRadius = 24.dp().toFloat() } }
        card.addView(featureIcon, LinearLayout.LayoutParams(76.dp(),76.dp()).apply { gravity = Gravity.CENTER; bottomMargin = 12.dp() })
        featureAnimator?.cancel()
        featureAnimator = ObjectAnimator.ofFloat(featureIcon, "translationY", -8f, 8f).apply { duration = 1800; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator(); start() }
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
        if(page>0) {
            val skip=TextView(this).apply{text="Skip Now";textSize=13f;gravity=Gravity.CENTER;setTextColor(Color.rgb(190,198,220));setPadding(8.dp(),12.dp(),8.dp(),4.dp());isClickable=true;setOnClickListener{prefs.edit().putBoolean("onboarding_complete",true).apply();openApp()}}
            card.addView(skip,LinearLayout.LayoutParams(-1,42.dp()).apply{topMargin=4.dp()})
        }
        attach()
    }

    private fun openApp(){startActivity(android.content.Intent(this,MainActivity::class.java));finish()}
    private fun deviceId()=Settings.Secure.getString(contentResolver,Settings.Secure.ANDROID_ID).orEmpty().ifBlank{Build.MANUFACTURER+"-"+Build.MODEL+"-"+Build.VERSION.SDK_INT}
    private fun attach(){root.removeView(card);root.addView(card,FrameLayout.LayoutParams(-1,-2).apply{gravity=Gravity.CENTER;setMargins(18.dp(),18.dp(),18.dp(),18.dp())})}
    private fun glass()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24.dp(),24.dp(),24.dp(),24.dp());background=GradientDrawable().apply{setColor(Color.argb(235,20,20,20));cornerRadius=28.dp().toFloat();setStroke(1.dp(),Color.argb(55,212,175,55))};elevation=10.dp().toFloat()}
    private fun logo()=ImageView(this).apply{setImageResource(R.drawable.rss_downloader_logo);scaleType=ImageView.ScaleType.CENTER_INSIDE;contentDescription="RSS Downloader"}
    private fun input(h:String)=EditText(this).apply{hint=h;setHintTextColor(Color.rgb(170,170,170));setTextColor(Color.WHITE);setSingleLine();setPadding(16.dp(),0,16.dp(),0);background=GradientDrawable().apply{setColor(Color.argb(30,255,255,255));cornerRadius=16.dp().toFloat()}}
    private fun button(s:String)=TextView(this).apply{text=s;textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.BLACK);typeface=android.graphics.Typeface.DEFAULT_BOLD;background=GradientDrawable().apply{setColor(Color.rgb(212,175,55));cornerRadius=16.dp().toFloat()}}
    private fun t(s:String,z:Int,b:Boolean)=TextView(this).apply{text=s;textSize=z.toFloat();setTextColor(Color.WHITE);typeface=if(b)android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT}
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
}

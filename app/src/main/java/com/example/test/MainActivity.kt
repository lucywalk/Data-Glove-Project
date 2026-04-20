package com.example.test //link to app

//import all necessary packages
import com.example.test.BLEManager  //add in BLE class with functions to connect etc
import android.Manifest
import java.io.File  //CSV files
import android.app.Activity
import android.bluetooth.*  //BLE classes
import android.bluetooth.le.ScanCallback //BLE classes
import android.bluetooth.le.ScanResult  //BLE classes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import com.example.test.ui.theme.TestTheme
import android.bluetooth.le.ScanSettings
import java.util.UUID
import kotlin.String
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Info
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.AlertDialogDefaults.containerColor
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.Intent
import android.icu.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun updateStreak(context: Context): Int {  //tracks daily usage streak
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) //opens local storage
    val lastOpened = prefs.getString("last_opened_date", null)  //checks when last opened
    val streak = prefs.getInt("streak", 0)
    val today = java.time.LocalDate.now()  //retrieves today's date by checking date on phone
    val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE  //put date into date format
    val newStreak = if (lastOpened != null) {
        val lastDate = java.time.LocalDate.parse(lastOpened, formatter)

        when {
            lastDate.isEqual(today.minusDays(1)) -> streak + 1  //if opened yesterday increase streak
            lastDate.isEqual(today) -> streak  //if opened today keep streak same
            else -> 1  //otherwise reset to one
        }
    } else {
        1
    }

    prefs.edit()  //save result
        .putString("last_opened_date", today.format(formatter))
        .putInt("streak", newStreak)
        .apply()

    return newStreak
}
fun saveToCSV(context: Context, data: List<String>) {  //saves session data into CSV file

    val fileName = "session_${System.currentTimeMillis()}.csv" //save filename with current time
    val file = File(context.getExternalFilesDir(null), fileName)

    file.printWriter().use { out ->  //write data into file in form 'time, thumb, etc'
        out.println("time,thumb,index,middle,ring,pinky")
        data.forEach { out.println(it) }
    }
}

fun getSavedSessions(context: Context): List<File> {  //returns saved CSV files
    val dir = context.getExternalFilesDir(null)
    return dir?.listFiles { file ->
        file.extension == "csv"  //filters files for only csv files
    }?.sortedByDescending { it.lastModified() } ?: emptyList()  //show newest file first
}

fun readCSV(file: File): List<String> {  //read CSV file into list of strings
    return file.readLines()
}

fun shareCSV(context: Context, file: File) {  //share CSV file using android share menu on phone
    val uri = androidx.core.content.FileProvider.getUriForFile(  //convert file into content uri as android does not allow direct file sharing due to security reasons
        context,
        "${context.packageName}.provider",  //unique id
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {  //tells android the app wants to send a file
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri) //attaches file
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  //grants temporary permission for other apps to read file
    }

    context.startActivity(
        Intent.createChooser(intent, "Download / Share CSV")  //opens system share sheet so user sees options such as gmail/whatsapp/google drive
    )
}

fun deleteAllCSVs(context: Context) {  //deletes all saved csv
    val dir = context.getExternalFilesDir(null)
    dir?.listFiles()?.forEach { file ->
        if (file.extension == "csv") {  //check for file type
            file.delete()
        }
    }
}

@Composable
fun LineGraph(  //draws a live graph of sensor data
    data: List<Float>,  //data comes in form of list
    threshold: Float,  //threshold set as a single value (draw upper threshold for visualisation)
    color: Color = Color.Black
) {
    Canvas(modifier = Modifier.fillMaxSize()) {  //set graph to fill available space

        val width = size.width
        val height = size.height
        if (data.size < 2) return@Canvas  //restart if not enough data points
        val stepX = width / (data.size - 1)  //set spacing between data points

        for (i in 0 until data.size - 1) {
            val x1 = i * stepX
            val x2 = (i + 1) * stepX
            val y1 = height - (data[i] / 100f) * height
            val y2 = height - (data[i + 1] / 100f) * height

            drawLine( //connect each data point
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                color = color,
                strokeWidth = 4f
            )
        }

        val high = height - (threshold / 100f) * height //calculate position of threshold relative to graph

        drawLine(  //draw threshold in red
            color = Color.Red,
            start = Offset(0f, high),
            end = Offset(width, high),
            strokeWidth = 4f
        )
    }
}

fun saveHighScore(context: Context, level: Int, levelScore: Int) {  //updates individual level high score
    val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    val currentHigh = prefs.getInt("level_$level", 0) //retrieves current high score for specific level

    if (levelScore > currentHigh) {  //only updates if new score is higher
        prefs.edit().putInt("level_$level", levelScore).apply() //saves in local storage
    }
}

fun getHighScore(context: Context, level: Int): Int {  //return saved high score for specific level
    val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    return prefs.getInt("level_$level", 0)
}

fun resetHighScores(context: Context, maxLevel: Int) {  //reset high score for all levels to zero
    val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    val editor = prefs.edit()

    for (level in 1..maxLevel) {
        editor.putInt("level_$level", 0)
    }

    editor.apply()
}


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {  //runs when app starts

        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("game", MODE_PRIVATE)  //get local storage
        enableEdgeToEdge() //fill screen

        setContent {
            TestTheme {  //sets UI

                var currentLevel by remember { //remember what level up to and lock off higher levels
                    mutableStateOf(prefs.getInt("level", 1))
                }
                var selectedLevel by remember { mutableStateOf<Int?>(null) }  //tracks which level user seleceted
                var score by remember { //remember current score
                    mutableStateOf(prefs.getInt("score", 0))
                }
                val context = LocalContext.current  //general app access
                val activity = context as Activity  //screen level control
                val bleManager = remember {  //BLE file
                    BLEManager(context, activity)
                }
                val results = remember { mutableStateMapOf<String, Int>() }
                var streak by remember { mutableStateOf(0) }  //remembers current streak

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->  //UI logic structure

                    if (selectedLevel == null) {  //if no level selected go to level select screen
                        LevelSelectScreen(  //pass parameters to this screen
                            unlockedLevel = currentLevel,
                            score = score,
                            onLevelSelected = { selectedLevel = it }, //if level is selected update selected level
                            onReset = {  //is reset button pressed, delete all progress and leave only level 1 unlocked
                                currentLevel = 1
                                score = 0
                                prefs.edit()
                                    .clear()
                                    .apply()
                                bleManager.clearProgress(context)
                                deleteAllCSVs(context)
                                resetHighScores(context, 6)
                            }
                        )
                    } else if (selectedLevel == 6) {

                        ProgressScreen(  //open progress screen
                            bleManager = bleManager,
                            onBack = { selectedLevel = null },
                            streak = streak
                        )

                    } else {
                        LevelScreen(  //overarching level screen shares common features for each individual level
                            level = selectedLevel!!,
                            score = score,
                            bleManager = bleManager,
                            onBack = { selectedLevel = null },  //if press back go to level select screen

                            onNextLevel = {
                                if (selectedLevel == currentLevel) {
                                    currentLevel++   // only unlock next level
                                }
                                score += 10  //add 10 to score when complete level
                                prefs.edit()  //update unlocked level and score
                                    .putInt("level", currentLevel)
                                    .putInt("score", score)
                                    .apply()
                                selectedLevel = null //go to level select screen when complete a level
                            },
                            onScoreAdd = {
                                score += it //add to score
                                prefs.edit()
                                    .putInt("score", score)
                                    .apply()
                            },
                            onStreakUpdate = {
                                streak = it  //update streak
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LevelSelectScreen(
    unlockedLevel: Int,  //parameters passed from main activity to this screen
    score: Int,
    onLevelSelected: (Int) -> Unit,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.padding(50.dp)) {
        Row(  //arrange buttons on row
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Level Selection",fontSize = 30.sp) //make it a header
            TextButton(
                onClick = { onReset() },  //when press reset call reset function
            ) {
                Text("Reset")
            }
        }

        Text("Score: $score")
        Spacer(modifier = Modifier.height(16.dp))

        for (i in 1..5) {
            Button( //display levels 1 to 5
                onClick = { onLevelSelected(i) }, //open level when selected
                enabled = i <= unlockedLevel,  //only allow to open unlocked levels
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Level $i")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = { onLevelSelected(6) },
            modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),   // background green
                    contentColor = Color.White            // text colour
                    )
        ) {
            Text("Progress")  //level 6 used as progress button
        }
    }
}

@Composable
fun LevelScreen(  //overarching level screen collates shared parameters between levels
    level: Int,
    score: Int,
    bleManager: BLEManager,
    onNextLevel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onScoreAdd: (Int) -> Unit,
    onStreakUpdate: (Int) -> Unit
) {
    var levelScore by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val highScore = getHighScore(context, level)
    var isCalibrated by remember { mutableStateOf(false) }  //flag to check if calibrated
    var isStarted by remember { mutableStateOf(false) }  //flag to check if start button has been pressed
    val status by bleManager.status.collectAsState()
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var levelComplete by remember { mutableStateOf(false) }  //flag to check if level complete
    val sessionData = remember { mutableStateListOf<String>() }
    val streak = updateStreak(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Level $level",
                fontSize = 35.sp
            )
            TextButton(onClick = onBack) {  //call function if press back button, text button so less attention
                Text("Back")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Score: $levelScore")
        Text("High Score: $highScore")

        LaunchedEffect(Unit) {  //when app opens set level score to zero and send 'c'
            isCalibrated = true
            bleManager.sendCalibrate()
            levelScore = 0
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if ((status != "Connected!"&& status != "Ready!" && status != "Streaming...")|| (status == "Not connected")) {
                Button(onClick = { bleManager.startScan() }) {
                    Text("Connect")  //display connect button if not connected or device becomes disconnected
                }
            }
            Row{
                if(status == "Ready!"|| status == "Streaming..."){
                    Button(onClick = {  //display calibrate button once connected
                        bleManager.sendCalibrate()  //send 'c'
                        isCalibrated = true   //set flag
                    }) {
                        Text("Calibrate")
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (status == "Streaming..." &&isStarted){  //display stop button once start button has been pressed
                        Button(onClick = {
                            bleManager.sendEnd()  //send 'e'
                            saveHighScore(context, level, levelScore)
                            onScoreAdd(levelScore)  //update score
                            onBack()  //go back to level select screen
                        }) {
                            Text("Stop")
                        }
                }
                Spacer(modifier = Modifier.width(16.dp))

                if (status == "Ready!" ||(status == "Streaming..." &&isCalibrated)) {  //display start button once connected or once calibrate pressed
                    Button(onClick = {
                        bleManager.sendStart()
                        isCalibrated = false   // optional reset
                        isStarted = true  //make start button disappear once pressed
                    }) {
                        Text("Start")
                    }
                }
            }
        }

        if (levelComplete) {  //display on screen if level completed

            Column(
                modifier = Modifier
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Level Complete 🎉",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                onStreakUpdate(streak)  //only update streak once level completed, not just when open app
                bleManager.sendEnd()  //send 'e'
                saveHighScore(context, level, levelScore)

                Button(onClick = {
                    levelComplete = false   // reset for next level
                    onNextLevel()  //unlock next level
                }) {
                    Text("Next Level", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }

                Button(onClick = {
                    saveToCSV(context, sessionData)  //save data
                }) {
                    Text("Save Session", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }

        when (level) {
            1 -> LevelOne( //define parameters for each level
                bleManager = bleManager,
                onNextLevel = onNextLevel,
                isCalibrated = isCalibrated,
                onLevelComplete = { levelComplete = true },
                onBack = onBack,
                sessionData = sessionData,
                onScoreUpdate = { levelScore += 1 }
            )
            2 -> LevelTwo(
                bleManager = bleManager,
                onNextLevel = onNextLevel,
                isCalibrated = isCalibrated,
                onLevelComplete = { levelComplete = true },
                onBack = onBack,
                sessionData = sessionData,
                onScoreUpdate = { levelScore += 1 }
            )
            3 -> LevelThree(
                bleManager = bleManager,
                onNextLevel = onNextLevel,
                isCalibrated = isCalibrated,
                onLevelComplete = { levelComplete = true },
                onBack = onBack,
                sessionData = sessionData,
                onScoreUpdate = { levelScore += 1 },
                isStarted = isStarted
            )
            4 -> LevelFour(
                bleManager = bleManager,
                onNextLevel = onNextLevel,
                isCalibrated = isCalibrated,
                onLevelComplete = { levelComplete = true },
                onBack = onBack,
                sessionData = sessionData,
                onScoreUpdate = { levelScore += 1 }
            )

            6 -> ProgressScreen(
                bleManager = bleManager,
                onBack = onBack,
                streak = streak
            )

            else -> Text("More levels coming soon...")
        }
    }
}

@Composable
fun Info(infoText: String) {  //put info text as parameter so can be customised for each level
    var showDialog by remember { mutableStateOf(false) } //flag to check if info button has been pressed

    IconButton(onClick = { showDialog = true }) {  //if info button pressed set flag high
        Icon(Icons.Default.Info, contentDescription = "Info") //info icon
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("More Information") },
            text = {
                Column {
                    Text(infoText)  //display info text
                    Spacer(modifier = Modifier.height(8.dp))
                }
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Close")  //close info
                }
            }
        )
    }
}

@Composable
fun LevelOne(
    bleManager: BLEManager,
    onNextLevel: () -> Unit,
    isCalibrated: Boolean,
    onLevelComplete: () -> Unit,
    onBack: () -> Unit,
    sessionData: MutableList<String>,
    onScoreUpdate: () -> Unit
) {
    var started by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var hasSentEnd by remember { mutableStateOf(false) }
    val status by bleManager.status.collectAsState()
    val index by bleManager.index.collectAsState()
    var bendCount by remember { mutableStateOf(0) }
    var isBent by remember { mutableStateOf(false) }
    val indexData = remember { mutableStateListOf<Float>() }
    val bendThreshold = 60f  //set thresholds for bending
    val restThreshold = 30f
    val filteredData = indexData.mapIndexed { i, _ ->  //filter data using moving average
        val start = maxOf(0, i - 4)
        indexData.subList(start, i + 1).average().toFloat()
    }

    LaunchedEffect(index) {
        val timestamp = System.currentTimeMillis()
        sessionData.add(
            "$timestamp,$index"
        )

        val windowSize = 5  //set filter size
        indexData.add(index)  //add data
        val filteredIndex = indexData.takeLast(windowSize).average().toFloat() //filter data using moving average

        if (indexData.size > 60) indexData.removeAt(0)  //only show latest 60 data entries so that graph is fixed in size

        if (!isBent && filteredIndex > bendThreshold) {
            isBent = true
        }

        if (isBent && filteredIndex < restThreshold) {
            isBent = false
            bendCount++  //increase bend count when finger goes above then below bend threshold
            onScoreUpdate()  //add 1 to score
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row{
            Text("Bend your index finger 10 times", fontWeight = FontWeight.Bold)  //display task
            Info("Initially lay your fingers as flat as possible. Then bend your index finger until it passes the red threshold line. Then extend it until flat and repeat.")
        }
        Text("Bends: $bendCount / 10")
        Text(status)  //show status so user can know if device connected etc
        Spacer(modifier = Modifier.height(50.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            LineGraph(
                data = filteredData, //plot filtered data
                threshold = bendThreshold
            )
    }
        Spacer(modifier = Modifier.height(30.dp))

        LaunchedEffect(bendCount) {
            if (bendCount >= 10 && !hasSentEnd) { //if reaches 10 bends
                bleManager.sendEnd()  //send 'e'
                hasSentEnd = true
            }
        }
        // ✅ LEVEL COMPLETE
        LaunchedEffect(bendCount) {
            if (bendCount >= 10) {
                onLevelComplete()  //perform level complete code
            }
        }
    }
}

@Composable
fun Legend(color: Color, label: String){  //legend for graph with multiple entries
    Row(verticalAlignment = Alignment.CenterVertically){
        Box(
            modifier = Modifier.size(12.dp).background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text=label)
    }
}

@Composable
fun LevelTwo(
    bleManager: BLEManager,
    onNextLevel: () -> Unit,
    isCalibrated: Boolean,
    onLevelComplete: () -> Unit,
    onBack: () -> Unit,
    sessionData: MutableList<String>,
    onScoreUpdate: () -> Unit
) {
    var started by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val status by bleManager.status.collectAsState()
    var hasSentEnd by remember { mutableStateOf(false) }

    val thumb by bleManager.thumb.collectAsState()  //collect finger data
    val index by bleManager.index.collectAsState()
    val middle by bleManager.middle.collectAsState()
    val ring by bleManager.ring.collectAsState()
    val pinky by bleManager.pinky.collectAsState()

    var bendCount by remember { mutableStateOf(0) }
    var isBent by remember { mutableStateOf(false) }

    val thumbData = remember { mutableStateListOf<Float>() }
    val indexData = remember { mutableStateListOf<Float>() }
    val middleData = remember { mutableStateListOf<Float>() }
    val ringData = remember { mutableStateListOf<Float>() }
    val pinkyData = remember { mutableStateListOf<Float>() }
    val filteredIndexData = indexData.mapIndexed { i, _ ->  //filter data sets using moving average
        val start = maxOf(0, i - 4)
        indexData.subList(start, i + 1).average().toFloat()
    }
    val filteredMiddleData = middleData.mapIndexed { i, _ ->
        val start = maxOf(0, i - 4)
        middleData.subList(start, i + 1).average().toFloat()
    }
    val filteredRingData = ringData.mapIndexed { i, _ ->
        val start = maxOf(0, i - 4)
        ringData.subList(start, i + 1).average().toFloat()
    }
    val filteredPinkyData = pinkyData.mapIndexed { i, _ ->
        val start = maxOf(0, i - 4)
        pinkyData.subList(start, i + 1).average().toFloat()
    }

    val bendThreshold = 35f
    val restThreshold = 20f

    LaunchedEffect( thumb,index, middle, ring, pinky) {

        val timestamp = System.currentTimeMillis()
        sessionData.add(
            "$timestamp,$thumb,$index,$middle,$ring,$pinky"
        )
        val windowSize = 5  //set size of filter
        indexData.add(index)
        middleData.add(middle)
        ringData.add(ring)
        pinkyData.add(pinky)

        val filteredIndex = indexData.takeLast(windowSize).average().toFloat()  //filter data using moving average
        val filteredMiddle = middleData.takeLast(windowSize).average().toFloat()
        val filteredRing = ringData.takeLast(windowSize).average().toFloat()
        val filteredPinky = pinkyData.takeLast(windowSize).average().toFloat()

        if (indexData.size > 35) indexData.removeAt(0)  //only show last 35 data points so graph does not keep growing
        if (middleData.size > 35) middleData.removeAt(0)
        if (ringData.size > 35) ringData.removeAt(0)
        if (pinkyData.size > 35) pinkyData.removeAt(0)

        val allBent =
            filteredIndex > bendThreshold &&
                    filteredMiddle > bendThreshold &&
                    filteredRing > bendThreshold &&
                    filteredPinky > bendThreshold

        val allRelaxed =
            filteredIndex < restThreshold &&
                    filteredMiddle < restThreshold &&
                    filteredRing < restThreshold &&
                    filteredPinky < restThreshold

        if (!isBent && allBent) {
            isBent = true
        }

        if (isBent && allRelaxed) {
            isBent = false  //increase bend count when all fingers pass the high then the low threshold
            bendCount++
            onScoreUpdate()  //update score
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row{
            Text("Bend ALL fingers 10 times", fontWeight = FontWeight.Bold)  //display task
            Info("Initially lay your fingers as flat as possible. Then bend your index, middle, ring and pinky fingers at the same time, until they all pass the red threshold line. Then extend your fingers until flat and repeat.")
        }

        Text("Bends: $bendCount / 10")
        Text(status)
        Spacer(modifier = Modifier.height(70.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    fun drawFinger(data: List<Float>, color: Color) {
                        if (data.size < 2) return
                        val stepX = width / (data.size - 1)

                        for (i in 0 until data.size - 1) {
                            val x1 = i * stepX
                            val x2 = (i + 1) * stepX
                            val y1 = height - (data[i] / 100f) * height
                            val y2 = height - (data[i + 1] / 100f) * height

                            drawLine(
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                color = color,
                                strokeWidth = 4f
                            )
                        }
                    }
                    drawFinger(filteredIndexData, Color.Blue)  //plot the fingers in different colours
                    drawFinger(filteredMiddleData, Color.Green)
                    drawFinger(filteredRingData, Color.Magenta)
                    drawFinger(filteredPinkyData, Color.Black)

                    //draw threshold line
                    val maxValue = 100f
                    val high = height - (bendThreshold/maxValue)*height

                    drawLine(
                        color = Color.Red,
                        start = Offset(0f, high),
                        end = Offset(width, high),
                        strokeWidth = 4f
                    )
            }
        }
        Spacer(modifier = Modifier.height(50.dp))
        Column{
            Legend(Color.Blue, "Index")  //create a legend for the different fingers
            Legend(Color.Green, "Middle")
            Legend(Color.Magenta, "Ring")
            Legend(Color.Black, "Pinky")
        }

        LaunchedEffect(bendCount) {
            if (bendCount >= 10 && !hasSentEnd) {
                bleManager.sendEnd() //send 'e'
                hasSentEnd = true
                onLevelComplete() //level complete code
            }
        }
    }
}

@Composable
fun LevelThree(
    bleManager: BLEManager,
    onNextLevel: () -> Unit,
    isCalibrated: Boolean,
    onLevelComplete: () -> Unit,
    onBack: () -> Unit,
    sessionData: MutableList<String>,
    onScoreUpdate: () -> Unit,
    isStarted: Boolean
) {
    val context = LocalContext.current
    val status by bleManager.status.collectAsState()
    //val time by bleManager.time.collectAsState()
    val index by bleManager.index.collectAsState()
    var bendCount by remember { mutableStateOf(0) }
    var isBent by remember { mutableStateOf(false) }
    val indexData = remember { mutableStateListOf<Float>() }
    val bendThreshold = 60f
    val restThreshold = 30f
    var timeLeft by remember { mutableStateOf(10) }  //set flags for timer
    var hasFinished by remember { mutableStateOf(false) }
    val windowSize = 5
    val filteredIndex = indexData.takeLast(windowSize).average().toFloat()  //filter data using moving average
    val filteredData = indexData.mapIndexed { i, _ ->
        val start = maxOf(0, i - 4)
        indexData.subList(start, i + 1).average().toFloat()
    }

    LaunchedEffect(isStarted) {
        if (isStarted) {
            timeLeft = 10
            bendCount = 0
            hasFinished = false
            bleManager.sendStart()  //send 's'

            while (timeLeft > 0) {
                delay(1000)
                timeLeft-- //count down timer
            }
            hasFinished = true  //set flag when timer complete
        }
    }

    LaunchedEffect(index) {

        if (!isStarted) return@LaunchedEffect

        val timestamp = System.currentTimeMillis()
        sessionData.add("$timestamp,$index")

        indexData.add(index)
        if (indexData.size > 60) indexData.removeAt(0) //only show last 60 data points

        if (!isBent && filteredIndex > bendThreshold) {
            isBent = true
        }

        if (isBent && filteredIndex < restThreshold) {
            isBent = false
            bendCount++  //increase bend count when pass high then low threshold
            onScoreUpdate()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row{
            Text("Bend your index finger as many times as possible in 10 seconds", fontWeight = FontWeight.Bold) //display task
            Info("Initially lay your fingers as flat as possible. Then bend your index finger until it passes the red threshold line. Then extend it until flat and repeat as many times as possible within the time limit.")
        }

        Text("Time Left: $timeLeft s")  //display remaining time
        Text("Number: $bendCount")
        Text(status)
        Spacer(modifier = Modifier.height(70.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            LineGraph(
                data = filteredData,  //plot data
                threshold = bendThreshold
            )
        }
        LaunchedEffect(hasFinished) {
            if (hasFinished) {  //once timer completes, automatically pass level
                onLevelComplete()
            }
        }
    }
}

@Composable
fun LevelFour(
    bleManager: BLEManager,
    onNextLevel: () -> Unit,
    isCalibrated: Boolean,
    onLevelComplete: () -> Unit,
    onBack: () -> Unit,
    sessionData: MutableList<String>,
    onScoreUpdate: () -> Unit
) {
    val context = LocalContext.current
    val status by bleManager.status.collectAsState()
    val thumb by bleManager.thumb.collectAsState()
    val index by bleManager.index.collectAsState()
    val middle by bleManager.middle.collectAsState()
    val ring by bleManager.ring.collectAsState()
    var step by remember { mutableStateOf(0) } // 0,1,2 pattern
    var score by remember { mutableStateOf(0) }
    var isHolding by remember { mutableStateOf(false) }
    val thumbBendThreshold = 15f  //lower thresholds for thumb as will not bend as much when just touching fingers
    val thumbRestThreshold = 10f
    val bendThreshold = 40f
    val restThreshold = 20f

    val pattern = listOf(
        "Thumb + Index",
        "Thumb + Middle",
        "Thumb + Ring"
    )

    LaunchedEffect(thumb, index, middle, ring) {

        val timestamp = System.currentTimeMillis()
        sessionData.add("$timestamp,$thumb,$index,$middle,$ring")

        val thumbBent = thumb > thumbBendThreshold
        val indexBent = index > bendThreshold
        val middleBent = middle > bendThreshold
        val ringBent = ring > bendThreshold

        val correctMove = when (step) {
            0 -> thumbBent && indexBent && !middleBent && !ringBent  //check only specific fingers bent
            1 -> thumbBent && middleBent && !indexBent && !ringBent
            2 -> thumbBent && ringBent && !indexBent && !middleBent
            else -> false
        }

        val allReleased =
            thumb < thumbRestThreshold &&
                    index < restThreshold &&
                    middle < restThreshold &&
                    ring < restThreshold

        if (!isHolding && correctMove) {
            isHolding = true  //checks performs bends in correct order
        }

        if (isHolding && allReleased) {
            isHolding = false
            onScoreUpdate()
            step++  //change to next position

            if (step > 2) {
                onLevelComplete()
                return@LaunchedEffect // stop further execution
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row{
            Text("Follow the pattern:", fontWeight = FontWeight.Bold) //display task
            Info("Initially lay your fingers as flat as possible. Follow the pattern described on the screen. For example, if it says thumb + index, bend your thumb and index until they touch each other. Then extend until your fingers are flat and follow the next instruction.")
        }
        Text(
            "Current: ${pattern.getOrNull(step) ?: "Done"}",
            fontWeight = FontWeight.Bold
        )
        Text(status)
        Spacer(modifier = Modifier.height(30.dp))
        /*Image(  //image could be added to further explain task
            painter = painterResource(id = R.drawable.example_image),
            contentDescription = "Example"
        )*/

    }
}

fun calculateMax(values: List<Float>): Float {
    return values.maxOrNull() ?: 0f
}

fun calculateImprovement(first: Float, last: Float): Int {  //calculate percentage change in bend between most recent and first bend
    if (first == 0f) return 0
    return (((last - first) / first) * 100).toInt()
}

@Composable
fun TableRow(label: String, max: Float, improvement: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            max.toInt().toString(),
            modifier = Modifier.weight(1f)
        )
        Text(
            "${if (improvement >= 0) "+" else ""}$improvement%",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ProgressScreen(
    bleManager: BLEManager,
    onBack: () -> Unit,
    streak: Int
) {
    val context = LocalContext.current
    val files = remember(context) { getSavedSessions(context) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var data by remember { mutableStateOf(bleManager.loadMaxData(context)) }

    LaunchedEffect(selectedFile) {
        selectedFile?.let {  //if select file read it
            lines = readCSV(it)
        }
    }

    val formatter = remember {  //format date
        java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
    }

    val thumb = data.map { it.thumb }
    val index = data.map { it.index }
    val middle = data.map { it.middle }
    val ring = data.map { it.ring }
    val pinky = data.map { it.pinky }
    val lastSession = data.lastOrNull()?.timestamp?.let { formatter.format(it) } ?: "N/A"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Progress Dashboard", style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = onBack) {
                Text("Back")  //go to level select screen
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Last session: $lastSession")
        Text("Streak: $streak days")  //display streak
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Finger", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)  //create table with these headings
            Text("Max", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Improvement", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Divider()

        TableRow("Thumb", calculateMax(thumb), calculateImprovement(thumb.firstOrNull() ?: 0f, thumb.lastOrNull() ?: 0f)) //calculate values for table
        TableRow("Index", calculateMax(index), calculateImprovement(index.firstOrNull() ?: 0f, index.lastOrNull() ?: 0f))
        TableRow("Middle", calculateMax(middle), calculateImprovement(middle.firstOrNull() ?: 0f, middle.lastOrNull() ?: 0f))
        TableRow("Ring", calculateMax(ring), calculateImprovement(ring.firstOrNull() ?: 0f, ring.lastOrNull() ?: 0f))
        TableRow("Pinky", calculateMax(pinky), calculateImprovement(pinky.firstOrNull() ?: 0f, pinky.lastOrNull() ?: 0f))

        Spacer(modifier = Modifier.height(40.dp))
        Text("Sessions", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(  //display CSV files in scrollable list
            modifier = Modifier.height(150.dp)
        ) {
            items(files) { file ->
                val formattedDate = remember(file.lastModified()) {  //use formatted date for file names
                    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                        .format(Date(file.lastModified()))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formattedDate, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            shareCSV(context, file)
                        }
                    ) {
                        Text("Download")  //option to download file
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        selectedFile?.let { file ->

            Spacer(modifier = Modifier.height(16.dp))
            Text("Viewing: ${file.name}", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.height(200.dp)
            ) {
                items(lines.drop(1)) { line -> // skip header
                    val parts = line.split(",")
                    Text(
                        text = "Index: ${parts.getOrNull(1) ?: "-"}", //view selected file
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

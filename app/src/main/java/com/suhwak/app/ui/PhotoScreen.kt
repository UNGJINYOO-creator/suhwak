package com.suhwak.app.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.Deck
import com.suhwak.app.data.MathText
import com.suhwak.app.data.PhotoProblems
import com.suhwak.app.data.PhotoProblems.Draft
import com.suhwak.app.data.Progress
import com.suhwak.app.data.SecretStore
import com.suhwak.app.data.StudySettings
import com.suhwak.app.net.Gemini
import com.suhwak.app.ui.theme.Sea
import kotlinx.coroutines.launch

/** 📷 사진 문제: 찍기(한 문제 / 한 페이지) → 제미나이가 읽기 → 부모가 확인·수정 → 단원 문제집에 저장. */
class PhotoViewModel(app: Application) : AndroidViewModel(app) {
    enum class Phase { PICK, READING, REVIEW, SAVED }

    private val dao = AppDatabase.get(app).wordDao()
    val settings = StudySettings(app)
    var phase by mutableStateOf(Phase.PICK)
        private set
    var page by mutableStateOf(false)
    var model by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var units by mutableStateOf<List<PhotoProblems.UnitInfo>>(emptyList())
        private set
    var unitNo by mutableStateOf(0)
    val drafts = mutableStateListOf<Draft>()
    var saved by mutableStateOf(0)
        private set

    val hasKey: Boolean get() = SecretStore.geminiKey(getApplication()) != null

    fun load() = viewModelScope.launch {
        val decks = dao.getAllDecks().associate { it.id to it.name }
        val seeds = dao.getAllWords().filter { it.isSeed }
        units = seeds.groupBy { Progress.unitOf(decks[it.deckId].orEmpty()) }.filterKeys { it != Int.MAX_VALUE }
            .map { (u, list) ->
                val name = list.map { decks[it.deckId].orEmpty() }.firstOrNull { Regex("""^\d+\.""").containsMatchIn(it) }
                    ?.substringBefore(" · ")?.substringAfter(". ")
                    ?: decks[list.first().deckId].orEmpty().substringBefore(" · ")
                PhotoProblems.UnitInfo(u, name, list.map { it.pos }.filter { it.isNotBlank() }.distinct())
            }.sortedBy { it.no }
    }

    fun read(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val key = SecretStore.geminiKey(app)
        if (key == null) {
            error = "설정 > 부모님 관리 > 📷 AI 사진 문제에서 제미나이 API 키를 먼저 넣어 주세요."
            return@launch
        }
        error = null
        phase = Phase.READING
        val image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Gemini.jpeg(app, uri) }
        if (image == null) {
            error = "사진을 열지 못했어요. 다시 찍어 주세요."
            phase = Phase.PICK
            return@launch
        }
        val twins = if (page) settings.photoTwinsPage else settings.photoTwinsOne
        val prompt = PhotoProblems.prompt(page, twins, units)
        when (val r = Gemini.ask(key, settings.geminiModelList, prompt, image) { model = it }) {
            is Gemini.Outcome.Ok -> {
                val parsed = PhotoProblems.parse(r.text)
                if (parsed == null) {
                    error = "제미나이 답을 문제로 바꾸지 못했어요. 다시 찍거나 직접 입력해 주세요."
                    phase = Phase.PICK
                } else {
                    drafts.clear()
                    drafts += parsed.drafts
                    unitNo = parsed.unitNo.takeIf { n -> units.any { it.no == n } } ?: units.firstOrNull()?.no ?: 0
                    phase = Phase.REVIEW
                }
            }
            is Gemini.Outcome.Failed -> {
                error = "사진을 읽지 못했어요.\n${r.message}\n\n직접 입력으로 문제를 넣을 수도 있어요."
                phase = Phase.PICK
            }
        }
    }

    /** 제미나이 없이 직접 넣기 (실패했을 때도). */
    fun manual() {
        error = null
        drafts.clear()
        drafts += PhotoProblems.blank()
        if (unitNo == 0) unitNo = units.firstOrNull()?.no ?: 0
        phase = Phase.REVIEW
    }

    fun addBlank() {
        drafts += PhotoProblems.blank(drafts.lastOrNull()?.concept.orEmpty())
    }

    fun save() = viewModelScope.launch {
        val name = PhotoProblems.deckName(unitNo, units.firstOrNull { it.no == unitNo }?.name.orEmpty())
        val deckId = dao.findDeckByName(name)?.id ?: dao.insertDeck(Deck(name = name, emoji = "📷"))
        val stamp = System.currentTimeMillis()
        val words = drafts.filter { it.keep }.mapIndexedNotNull { i, d -> d.toWord(deckId, "photo_${stamp}_$i") }
        if (words.isEmpty()) {
            error = "저장할 문제가 없어요. 문제와 정답을 채웠는지 확인해 주세요."
            return@launch
        }
        dao.insertWords(words)
        saved = words.size
        error = null
        phase = Phase.SAVED
    }

    fun again() {
        drafts.clear()
        error = null
        phase = Phase.PICK
    }
}

@Composable
fun PhotoScreen(onBack: () -> Unit, vm: PhotoViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) vm.read(Uri.parse(uri))
        pendingUri = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.read(uri)
    }
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("📷 사진으로 문제 만들기", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                when (vm.phase) {
                    PhotoViewModel.Phase.PICK -> Pick(vm, onCamera = {
                        val uri = newPhotoUri(context)
                        pendingUri = uri.toString()
                        takePicture.launch(uri)
                    }, onAlbum = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    PhotoViewModel.Phase.READING -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Sea.Mint)
                        Text("제미나이가 문제를 읽고 풀이를 만드는 중이에요…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                        if (vm.model.isNotEmpty()) Text(vm.model, style = MaterialTheme.typography.bodySmall, color = Sea.Soft)
                        Text("30초쯤 걸릴 수 있어요", style = MaterialTheme.typography.bodySmall, color = Sea.Soft)
                    }
                    PhotoViewModel.Phase.REVIEW -> Review(vm)
                    PhotoViewModel.Phase.SAVED -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    ) {
                        Text("🌱", fontSize = 56.sp)
                        Text(
                            "${vm.saved}문제를 심었어요!\n오늘의 바구니에서 먼저 나와요.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        PrimaryButton("📷 한 장 더 찍기", vm::again)
                        TextButton(onClick = onBack) { Text("돌아가기") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pick(vm: PhotoViewModel, onCamera: () -> Unit, onAlbum: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("무엇을 찍을까요?", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeTile("🔍", "한 문제", "가장 크게 보이는 문제 + 쌍둥이 ${vm.settings.photoTwinsOne}개", !vm.page, Modifier.weight(1f)) { vm.page = false }
            ModeTile("📄", "한 페이지", "보이는 문제 모두 + 문제마다 쌍둥이 ${vm.settings.photoTwinsPage}개", vm.page, Modifier.weight(1f)) { vm.page = true }
        }
        if (!vm.hasKey) {
            Text(
                "제미나이 API 키가 없어요. 설정 > 부모님 관리 > 📷 AI 사진 문제에서 넣어 주세요. 키 없이도 직접 입력은 할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        PrimaryButton("📷 사진 찍기", onCamera, Modifier.fillMaxWidth(), enabled = vm.hasKey)
        SecondaryButton("🖼️ 앨범에서 고르기", onAlbum, Modifier.fillMaxWidth(), enabled = vm.hasKey)
        SecondaryButton("✏️ 직접 입력", vm::manual, Modifier.fillMaxWidth())
        vm.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = Sea.FruitText,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sea.FruitSoft).padding(12.dp),
            )
        }
        Text(
            "사진과 만든 문제는 이 기기(와 가족 기기 동기화) 안에만 저장돼요. 문제 글자를 그대로 베끼지 않고 같은 뜻으로 다시 써요.",
            style = MaterialTheme.typography.bodySmall,
            color = Sea.Soft,
        )
    }
}

@Composable
private fun ModeTile(icon: String, title: String, sub: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Sea.MintCardOpen else Sea.Lavender).clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 30.sp)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Sea.Deep)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = Sea.Soft, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Review(vm: PhotoViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("저장하기 전에 확인해 주세요", style = MaterialTheme.typography.titleMedium)
            Text(
                "AI가 만든 정답과 풀이는 틀릴 수 있어요. 고칠 곳은 고치고, 넣지 않을 문제는 체크를 풀어 주세요. 풀이 줄의 [[값]]이 3차 빈칸이 돼요.",
                style = MaterialTheme.typography.bodySmall,
                color = Sea.Soft,
            )
            Text("단원", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.units.forEach { u ->
                    val on = u.no == vm.unitNo
                    Text(
                        "${u.no}. ${u.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) Sea.Deep else Sea.Soft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (on) Sea.MintCardOpen else Sea.Lavender)
                            .clickable { vm.unitNo = u.no }.padding(vertical = 8.dp, horizontal = 12.dp),
                    )
                }
            }
        }
        itemsIndexed(vm.drafts) { i, d ->
            DraftCard(i, d, vm.drafts.getOrNull(d.twinOf)) { vm.drafts[i] = it }
        }
        item {
            TextButton(onClick = vm::addBlank) { Text("➕ 문제 하나 더 직접 넣기") }
            vm.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Sea.FruitText) }
            PrimaryButton(
                "🌱 ${vm.drafts.count { it.keep }}문제 저장",
                vm::save,
                Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = vm.drafts.any { it.keep } && vm.unitNo > 0,
            )
            TextButton(onClick = vm::again, modifier = Modifier.fillMaxWidth()) { Text("다시 찍기") }
        }
    }
}

@Composable
private fun DraftCard(index: Int, d: Draft, original: Draft?, onChange: (Draft) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (d.twinOf >= 0) Sea.Lavender else Sea.MintCard).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = d.keep, onCheckedChange = { onChange(d.copy(keep = it)) })
            Text(
                if (d.twinOf >= 0) "↳ 쌍둥이" + (original?.concept?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") else "문제 ${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Sea.Deep,
                modifier = Modifier.weight(1f),
            )
            Text(if (d.mc) "객관식" else "주관식", style = MaterialTheme.typography.labelMedium, color = Sea.Soft)
        }
        if (!d.keep) return@Column
        Field("문제 (수식은 \$...\$)", d.question, lines = 3) { onChange(d.copy(question = it)) }
        if (d.question.contains('$')) {
            Text("미리 보기: " + MathText.plain(d.question), style = MaterialTheme.typography.bodySmall, color = Sea.Soft)
        }
        Field("보기 (한 줄에 하나, 주관식이면 비워 두기)", d.choices.joinToString("\n"), lines = 3) {
            onChange(d.copy(choices = it.split('\n')))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field(if (d.mc) "정답 (①~⑤)" else "정답", d.answer) { onChange(d.copy(answer = it)) } }
            Box(Modifier.weight(1f)) { Field("유형", d.concept) { onChange(d.copy(concept = it)) } }
        }
        Field("힌트 (정답은 말하지 않기)", d.hint) { onChange(d.copy(hint = it)) }
        Field("빈칸 풀이 (한 줄에 하나, 빈칸은 [[값]])", d.steps.joinToString("\n"), lines = 4) {
            onChange(d.copy(steps = it.split('\n')))
        }
        Field("풀이 설명", d.explanation, lines = 3) { onChange(d.copy(explanation = it)) }
    }
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = lines == 1,
        minLines = 1,
        maxLines = if (lines == 1) 1 else lines + 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

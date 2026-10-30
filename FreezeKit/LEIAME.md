# FreezeKit — Etapa 3

## Como sair do quiosque se algo der errado

**Saída normal de desenvolvimento:** conecte o tablet ao computador com Depuração USB autorizada. Abra o painel de emergência pelo ADB:

```bat
adb devices
adb shell am start -n freezekit.capitaldascestas/.StatusActivity
```

No painel, toque em **Sair do quiosque (DEV)**. O app para o Lock Task, restaura a barra de status, remove a preferência de tela inicial, retira o FreezeKit da lista Lock Task e abre o launcher Android. O painel é uma Activity exportada do mesmo pacote que está na lista Lock Task, por isso pode ser iniciado explicitamente pelo ADB durante o quiosque.

**Se a tela não responder:** como este ZIP é um APK de desenvolvimento com `android:testOnly="true"`, no terminal do computador execute:

```bat
adb shell dpm remove-active-admin --user 0 freezekit.capitaldascestas/.AdminReceiver
adb shell am task lock stop
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

A documentação oficial dos comandos ADB informa que `dpm remove-active-admin` exige `android:testOnly` e também remove Device Owners. Depois, a tela inicial HOME é aberta pelo terceiro comando.

**INCERTEZA:** `adb shell am task lock stop` existe no código-fonte AOSP Android 16, mas não é uma API pública estável do SDK nem há garantia de que todo firmware de fabricante mantenha esse comando. Se ele falhar ou não estiver disponível no VAIO, o comando documentado `dpm remove-active-admin` é a saída de desenvolvimento a tentar primeiro; o cabo USB, o computador e a autorização de depuração precisam continuar acessíveis.

Não distribua este APK de desenvolvimento como versão de produção. A remoção do Device Owner encerra a administração do app no tablet.

## O que esta etapa implementa

Esta versão parte do projeto Etapa 2 e implementa somente os requisitos RF-03 a RF-07: a `MainActivity` passa a ser a tela HOME preferida do Android; o próprio pacote é autorizado e iniciado em Lock Task; as funções de Lock Task são limitadas a `LOCK_TASK_FEATURE_KEYGUARD`; e o Device Owner desativa a barra de status. A tela de status provisória continua disponível para desenvolvimento e recebe os botões **Entrar no quiosque (DEV)** e **Sair do quiosque (DEV)**.

O app só aplica políticas do quiosque se for Device Owner. Depois de configurar a allowlist, o código confere `isLockTaskPermitted()` antes de chamar `startLockTask()` para evitar que uma autorização ausente coloque o app em fixação de tela comum. Se não for Device Owner, a navegação da Etapa 2 continua funcionando sem chamar APIs de política do quiosque.

**Fora do escopo:** bloqueios M5, TOTP, tela de administrador, configuração ou aplicação de política EFRP, novas permissões, restrições adicionais de usuário, configuração editável, logs e funções das etapas futuras. A consulta EFRP somente de leitura exibida pelo painel provisório é a função herdada da Etapa 1 e foi mantida sem alteração. Não foi adicionado nenhum bloqueio de sistema além de `setStatusBarDisabled(true)` e das funções de Lock Task pedidas nesta etapa.

## Requisitos e versões preservados

- Pacote e namespace: `freezekit.capitaldascestas`.
- `minSdk 30`, `targetSdk 36`, `compileSdk 36`.
- Android Gradle Plugin 8.10.0, Kotlin 2.2.0, Gradle Wrapper 8.13.
- Gradle Kotlin DSL e `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`.
- `android:testOnly="true"` e as três permissões que já existiam: INTERNET, ACCESS_NETWORK_STATE e CAMERA.
- Versão do app: `versionCode 3`, `versionName "0.3"`.

O MainActivity corrigido anexado pelo usuário foi usado como base, preservando o import `android.os.Build` e o callback de voltar com `OnBackInvokedDispatcher`.

## Abrir e compilar

1. Extraia o ZIP e abra no Android Studio a pasta raiz `FreezeKit` — a pasta que contém `settings.gradle.kts` e `gradlew.bat`.
2. Use JDK 21 como Gradle JDK e instale Android SDK Platform 36 pelo SDK Manager, se necessário.
3. No terminal do Android Studio, na pasta raiz, compile:

   ```bat
   .\gradlew.bat assembleDebug
   ```

4. O APK será criado em:

   ```text
   app\build\outputs\apk\debug\app-debug.apk
   ```

O Gradle Wrapper está incluído. A primeira compilação precisa de internet para baixar Gradle e as dependências do Android.

## Instalar ou atualizar

O tablet piloto já é Device Owner. Atualize o APK usando a mesma chave de depuração da instalação anterior:

```bat
adb devices
adb install -t -r app\build\outputs\apk\debug\app-debug.apk
```

Não execute `set-device-owner` novamente se o painel já mostrar `Device Owner: SIM`. Em um primeiro provisionamento de um tablet elegível, o comando seria:

```bat
adb shell dpm set-device-owner --user 0 freezekit.capitaldascestas/.AdminReceiver
```

A configuração de Device Owner deve ser feita com um único usuário e sem contas no tablet, conforme o procedimento de implantação.

## Como o quiosque funciona

A `MainActivity` mantém o filtro `MAIN` com `LAUNCHER`, `HOME` e `DEFAULT`. Quando o app é Device Owner, `KioskManager.entrarNoQuiosque()` registra a `MainActivity` como HOME persistente para `MAIN + HOME + DEFAULT`, coloca somente `freezekit.capitaldascestas` na allowlist Lock Task, define somente `LOCK_TASK_FEATURE_KEYGUARD`, chama `setStatusBarDisabled(true)` e inicia `startLockTask()` quando a `MainActivity` entra em `onResume()`.

O pacote autorizado contém tanto a `MainActivity` quanto a `StatusActivity`. Por isso, no build de desenvolvimento, o painel de status pode ser aberto explicitamente pelo ADB mesmo com Lock Task ativo. A Activity do painel está exportada e não possui filtro de launcher.

O Lock Task permite apenas o app incluído na allowlist. O app não habilita HOME, OVERVIEW, NOTIFICATIONS, SYSTEM_INFO nem GLOBAL_ACTIONS. O recurso KEYGUARD permanece ligado para que a tela de bloqueio do Android continue disponível. Como GLOBAL_ACTIONS não está habilitado, o menu de ações globais obtido ao segurar o botão de energia pode não aparecer enquanto o quiosque estiver ativo; por isso, mantenha o caminho de recuperação por USB/ADB durante os testes.

Se o FreezeKit não for Device Owner, `KioskManager` retorna sem aplicar políticas. A tela e o navegador seguem o comportamento da Etapa 2.

## Botões DEV do painel de status

Abra o painel pelo computador:

```bat
adb shell am start -n freezekit.capitaldascestas/.StatusActivity
```

- **Entrar no quiosque (DEV):** aplica as políticas de Device Owner e abre a `MainActivity`; é ela que chama `startLockTask()` no seu `onResume()`.
- **Sair do quiosque (DEV):** chama `stopLockTask()`, restaura a barra de status, limpa a preferência HOME persistente e esvazia a lista Lock Task. Em seguida, o app abre a tela HOME Android que ficou disponível após remover a preferência do FreezeKit.

A Activity que iniciou Lock Task é a que pode encerrá-lo diretamente com `stopLockTask()`. O botão também remove o pacote da allowlist por `DevicePolicyManager`, mecanismo que permite ao Device Owner encerrar remotamente o Lock Task; assim a saída não depende apenas de chamar `stopLockTask()` a partir do painel.

## Roteiro de teste RF-03 a RF-07

1. **RF-03 — tela HOME fixa:** depois de instalar a versão 0.3 com Device Owner, volte à tela inicial do tablet. A `MainActivity` deve abrir como HOME, sem seletor de launcher. Confirme também que o FreezeKit ainda aparece como app no launcher pelo filtro `LAUNCHER` quando fora do Lock Task.
2. **RF-04 — Lock Task:** confirme que o CONFER abre e que o HOME e os apps recentes não permitem sair do app. Somente o pacote FreezeKit deve estar na allowlist. Abra o painel de desenvolvimento via ADB para confirmar que ele é iniciado no mesmo pacote permitido.
3. **RF-05 — tela de bloqueio:** com uma senha Android já configurada, pressione o botão de energia para apagar a tela e depois desbloqueie com a senha. O quiosque deve voltar ao CONFER.
4. **RF-06 — barra e energia:** tente puxar a barra superior; ela não deve abrir. Verifique que não aparece um botão de energia no menu de Lock Task. O menu global do botão de energia também não foi habilitado, conforme a lista de recursos solicitada; use a saída por ADB se precisar sair durante o teste.
5. **RF-07 — retorno ao app:** em um teste controlado, reinicie o tablet com o quiosque ativo e confirme que a HOME preferida abre o FreezeKit e o `onResume()` reaplica o Lock Task. Não faça teste de travamento no tablet de produção sem manter um computador conectado para recuperação.
6. **Entrada e saída DEV:** inicie `StatusActivity` com `adb shell am start -n freezekit.capitaldascestas/.StatusActivity`. Toque em **Sair do quiosque (DEV)**: deve voltar à HOME Android. Abra novamente o painel por ADB, toque em **Entrar no quiosque (DEV)** e confirme que a `MainActivity` abre e fica bloqueada.
7. **Sem Device Owner:** somente em um tablet de teste, remova o Device Owner via ADB e abra o FreezeKit. Ele deve funcionar como navegador da Etapa 2; as chamadas Lock Task, preferência persistente HOME e status bar do quiosque não devem ser executadas.

## Comandos de desenvolvimento

Para abrir o painel:

```bat
adb shell am start -n freezekit.capitaldascestas/.StatusActivity
```

Para remover o Device Owner em um teste (`testOnly` precisa permanecer no Manifest):

```bat
adb shell dpm remove-active-admin --user 0 freezekit.capitaldascestas/.AdminReceiver
```

Para registrar novamente o Device Owner em um tablet de teste elegível:

```bat
adb shell dpm set-device-owner --user 0 freezekit.capitaldascestas/.AdminReceiver
```

Depois de remover o Device Owner, a desinstalação é:

```bat
adb uninstall freezekit.capitaldascestas
```

## Arquivos principais

- `app/src/main/java/freezekit/capitaldascestas/MainActivity.kt`: navegador da Etapa 2 baseado no arquivo corrigido anexado, mais a entrada no quiosque no `onResume()` quando Device Owner.
- `app/src/main/java/freezekit/capitaldascestas/kiosk/KioskManager.kt`: centraliza as operações `entrarNoQuiosque()` e `sairDoQuiosque()`.
- `app/src/main/java/freezekit/capitaldascestas/StatusActivity.kt`: painel provisório e botões de teste/saída DEV.
- `app/src/main/AndroidManifest.xml`: preserva as permissões existentes e adiciona HOME/DEFAULT ao filtro da MainActivity, mantendo LAUNCHER.
- `app/build.gradle.kts`: conserva as versões técnicas e atualiza `versionCode`/`versionName` para 3/0.3.
- `config/BrowserConfig.kt` e `web/BrowserUrlPolicy.kt`: configuração e validação de URL da Etapa 2.
- `AdminReceiver.kt` e `res/xml/device_admin.xml`: receptor Device Owner preservado.
- `gradle/wrapper/`, `gradlew` e `gradlew.bat`: Wrapper Gradle 8.13.

## Documentação Android

As APIs usadas são públicas e documentadas: [Lock task mode para dedicated devices](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode), [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager), [Activity](https://developer.android.com/reference/android/app/Activity) e [ADB e comandos DPM](https://developer.android.com/tools/adb). O comando `am task lock stop` é tratado separadamente como incerto porque é um comando de shell, não uma API pública estável do SDK.

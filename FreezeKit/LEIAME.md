# FreezeKit — Etapa 2

Esta versão parte do projeto aprovado da Etapa 1 e implementa o navegador interno previsto para os requisitos RF-08 a RF-13. O app abre o CONFER em uma WebView, só permite navegação para HTTPS nos hosts configurados e mantém um painel de status provisório acessível por ADB.

**Fora do escopo desta etapa:** modo quiosque/Lock Task, tela inicial fixa, bloqueios do Android, TOTP, modo administrador, edição da configuração, registro de eventos e aplicação de política EFRP.

## O que mudou

O `applicationId` e o `namespace` continuam `freezekit.capitaldascestas`. A versão passou para `versionCode 2` e `versionName "0.2"`. Permanecem `minSdk 30`, `targetSdk 36`, `compileSdk 36`, Android Gradle Plugin 8.10.0, Kotlin 2.2.0, Gradle Wrapper 8.13, Gradle Kotlin DSL, `compilerOptions` com JVM 17 e `android:testOnly="true"`.

A configuração é lida por uma classe própria baseada em `SharedPreferences`. Se ainda não há valores salvos, a classe usa os padrões da especificação:

- Página inicial: `https://confer.manus.space/`
- Hosts de navegação: `confer.manus.space` e `precodebanana.manus.space`
- Host autorizado para câmera: `confer.manus.space`

Não há tela para editar esses valores nesta etapa. O nome do host é comparado por igualdade completa, sem curingas ou aceitação de subdomínios; somente URLs HTTPS passam.

## Requisitos para compilar

- Windows com Android Studio instalado.
- JDK 21 selecionado como **Gradle JDK** no Android Studio.
- Android SDK Platform 36 instalado pelo SDK Manager.
- Tablet VAIO TL10 conectado por USB com Depuração USB ativada e autorizada.
- Internet na primeira compilação, para baixar Gradle e ferramentas/dependências do Android.

## Abrir o projeto e gerar o APK de depuração

1. Extraia o ZIP. A pasta do projeto chama-se `FreezeKit`.
2. No Android Studio, escolha **Open** e selecione a pasta `FreezeKit` extraída. Escolha a pasta raiz, onde estão `settings.gradle.kts` e `gradlew.bat`, e não a pasta `app`.
3. Aguarde a sincronização. Se o Android Studio solicitar o Android SDK Platform 36, aceite a instalação.
4. Em **File > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK**, selecione o JDK 21 usado no computador de desenvolvimento.
5. No terminal do Android Studio, aberto na pasta raiz, execute:

   ```bat
   .\gradlew.bat assembleDebug
   ```

6. O APK será criado em:

   ```text
   app\build\outputs\apk\debug\app-debug.apk
   ```

## Instalar ou atualizar no tablet

1. Confira a conexão USB:

   ```bat
   adb devices
   ```

   O tablet deve aparecer como `device`; aceite a autorização de depuração na tela do tablet se ela aparecer.

2. Instale ou atualize a versão de teste a partir da pasta raiz do projeto:

   ```bat
   adb install -t -r app\build\outputs\apk\debug\app-debug.apk
   ```

   `-t` é necessário por causa de `android:testOnly="true"`. `-r` atualiza a instalação existente. Para atualizar uma versão já instalada, compile e assine o APK com a mesma chave de depuração usada na Etapa 1.

3. O app FreezeKit continua abrindo pela tela inicial. Também pode ser iniciado explicitamente:

   ```bat
   adb shell am start -n freezekit.capitaldascestas/.MainActivity
   ```

## Comandos Device Owner

A Etapa 1 já deixou o tablet piloto como Device Owner. Ao instalar a atualização assinada com a mesma chave, **não execute `set-device-owner` novamente se o painel já indicar `SIM`**.

Se estiver fazendo a primeira configuração em um tablet elegível, o comando é:

```bat
adb shell dpm set-device-owner --user 0 freezekit.capitaldascestas/.AdminReceiver
```

O tablet deve estar sem contas e com um único usuário. O próprio Android pode recusar a operação se o estado do tablet não for elegível ou se já existir outro proprietário.

Para remover o Device Owner em um teste, use:

```bat
adb shell dpm remove-active-admin --user 0 freezekit.capitaldascestas/.AdminReceiver
```

Para desinstalar depois de removê-lo:

```bat
adb uninstall freezekit.capitaldascestas
```

## Painel de status provisório

A `StatusActivity` não tem filtro de launcher e não deve aparecer como um ícone separado na lista de apps. Para abri-la pelo computador:

```bat
adb shell am start -n freezekit.capitaldascestas/.StatusActivity
```

Ela mantém as informações de status da Etapa 1, o botão **Atualizar** e dois botões temporários para testar URLs que precisam ser bloqueadas: Google e o subdomínio `teste.manus.space`. A tela principal recebe a tentativa e aplica a mesma verificação de navegação da WebView.

## Como funciona a permissão de câmera

Quando o app é Device Owner, ele pede ao `DevicePolicyManager` para conceder `CAMERA` ao próprio FreezeKit. Quando não é Device Owner e uma página autorizada solicita vídeo, o Android mostra o diálogo normal de permissão de câmera. A WebView só recebe `RESOURCE_VIDEO_CAPTURE` se a origem for HTTPS e o host estiver na lista de hosts com câmera. Se a página pedir vídeo junto com áudio, o app concede somente vídeo; microfone e todos os outros recursos são negados.

A chamada de grant para Device Owner e a permissão normal do Android autorizam o app a usar a câmera. O app não acessa nem grava imagens por conta própria; o recurso é concedido à página através da WebView conforme a origem permitida.

## Roteiro de teste no tablet — itens 1 a 11

1. **Tela principal, fullscreen, JavaScript e DOM Storage:** abra FreezeKit. Deve aparecer o CONFER, sem barras de status/navegação. Faça um gesto de borda: o Android pode mostrar as barras temporariamente e ocultá-las de novo. Confirme que a página carrega e mantém o comportamento web necessário do CONFER.

2. **Configuração padrão:** abra FreezeKit após instalar. Deve iniciar em `https://confer.manus.space/`. A navegação para `precodebanana.manus.space` deve funcionar quando iniciada pelo CONFER. A lista é configurada por `BrowserConfig`/`SharedPreferences` e ainda não tem editor nesta etapa.

3. **HTTPS e igualdade exata do host:** abra o painel provisório pelo comando ADB abaixo e toque em **Testar bloqueio do Google** e **Testar bloqueio do subdomínio**. Em ambos os casos, a página não deve abrir e deve aparecer somente o aviso **Endereço não permitido**, sem o URL. O primeiro botão testa um host externo; o segundo confirma que `teste.manus.space` não é aceito como curinga de `*.manus.space`.

   ```bat
   adb shell am start -n freezekit.capitaldascestas/.StatusActivity
   ```

4. **Links `target=_blank` e `window.open`:** no CONFER, abra a página secundária permitida. Ela deve substituir a página na mesma WebView, sem janela nova; use o botão Voltar do Android para retornar. Se o link apontar para um host não permitido, deve ser bloqueado pelo mesmo aviso genérico.

5. **Câmera e microfone:** com Device Owner ativo, abra a função de leitura de código do CONFER; a permissão `CAMERA` deve estar concedida pelo `DevicePolicyManager` e o vídeo deve funcionar. Confirme que o app não solicita microfone. Para testar o fluxo sem Device Owner, remova-o temporariamente, revogue a permissão de câmera e abra a função de câmera; o Android deve pedir a permissão normal. Depois, conceda-a ou negue-a e confira o comportamento. Não tente abrir um host que não esteja na lista de câmera: a solicitação deve ser negada.

   ```bat
   adb shell dpm remove-active-admin --user 0 freezekit.capitaldascestas/.AdminReceiver
   adb shell pm revoke freezekit.capitaldascestas android.permission.CAMERA
   ```

   Depois do teste sem Device Owner, se o tablet estiver elegível e você quiser restaurar o estado da Etapa 1:

   ```bat
   adb shell dpm set-device-owner --user 0 freezekit.capitaldascestas/.AdminReceiver
   ```

6. **Downloads, seletor de arquivos e pressão longa:** em um formulário ou link disponível no CONFER, tente anexar arquivo, baixar arquivo e manter o dedo pressionado sobre um link. O app não deve abrir seletor, iniciar download nem mostrar o menu de pressão longa.

7. **Sem conexão e recuperação:** com a página aberta, desligue o Wi-Fi. Se a página principal falhar, deve aparecer **Sem conexão com a internet**, sem mostrar nenhum endereço. Ligue o Wi-Fi novamente e aguarde a rede recuperar conectividade; a WebView deve recarregar a última página autorizada automaticamente.

8. **Saída do processo de renderização da WebView:** o callback `onRenderProcessGone` cria outra WebView e tenta carregar a página inicial. O app não contém botão para provocar uma falha artificial, porque isso seria um recurso de teste fora do escopo solicitado. Para um teste dinâmico, é necessário um harness/ferramenta de depuração que encerre apenas o renderer da WebView. `adb shell am force-stop` encerra o processo do app e **não** testa esse callback. Sem esse harness, confira a implementação e valide a recuperação em teste instrumentado antes da implantação.

9. **Proteções da WebView:** confirme que a navegação só aceita HTTPS e host exato; HTTP e esquemas não HTTPS são barrados. O código também mantém acesso a arquivos e conteúdo local desativado, conteúdo misto bloqueado, geolocalização negada, nenhuma interface JavaScript registrada e Safe Browsing ativado. Um teste manual não deve tentar abrir arquivos locais nem usar uma página de phishing real; confira essas configurações no código/build.

10. **Botão Voltar:** navegue do CONFER para a página secundária permitida e pressione Voltar: deve retornar no histórico da WebView. Na página inicial, pressione Voltar sem histórico: o app deve permanecer aberto e não deve sair para a tela inicial.

11. **StatusActivity sem launcher e botões de teste:** confirme que o app normal abre o navegador e que não há um segundo ícone do painel. Execute `adb shell am start -n freezekit.capitaldascestas/.StatusActivity`; o painel deve exibir versão do app, Android/API, Device Owner e EFRP. Toque nos dois botões de teste: cada endereço deve ser bloqueado pelo aviso genérico. Para testar o segundo botão depois que a MainActivity foi aberta, execute novamente o comando ADB do painel.

## Arquivos principais

- `MainActivity.kt`: WebView fullscreen, política de navegação, câmera, tela offline, recuperação do renderer e botão Voltar.
- `StatusActivity.kt`: painel provisório da Etapa 1 e botões de teste RF-09, sem entrada de launcher.
- `config/BrowserConfig.kt`: configuração persistida em `SharedPreferences` com os valores padrão da especificação.
- `web/BrowserUrlPolicy.kt`: validação de esquema HTTPS e igualdade exata dos hosts.
- `AndroidManifest.xml`: permissões INTERNET, ACCESS_NETWORK_STATE e CAMERA, atividades e receptor administrativo.
- `app/build.gradle.kts`: pacote, SDKs, `versionCode 2`, `versionName 0.2` e alvo de compilação do Kotlin.
- `AdminReceiver.kt` e `res/xml/device_admin.xml`: base Device Owner preservada da Etapa 1.
- `gradle/wrapper/`, `gradlew` e `gradlew.bat`: Gradle Wrapper 8.13.

## APIs públicas e limites conhecidos

A navegação usa as APIs públicas de WebView/WebSettings/WebViewClient/WebChromeClient; a câmera usa `DevicePolicyManager.setPermissionGrantState` quando o app é Device Owner e a solicitação normal da Activity quando não é. A consulta EFRP do painel continua sendo somente leitura. O método pode retornar `null` se ainda não houver política EFRP configurada; isso não é alterado nesta etapa.

O teste de `onRenderProcessGone` precisa de um ambiente de teste que encerre especificamente o renderer da WebView; não foi incluído um botão artificial no app. O ambiente de geração deste ZIP não tem o compilador Java (`javac`) e o Android SDK Platform 36 necessários para executar `assembleDebug`; valide a compilação no Android Studio/JDK 21 da máquina de desenvolvimento.

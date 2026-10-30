# FreezeKit — Etapa 1

Projeto Android nativo em Kotlin para a Etapa 1 da especificação v1.1. Esta entrega contém somente a base de Device Owner e um painel provisório de status. **Ainda não há modo quiosque, WebView, bloqueios, TOTP nem configuração/aplicação da política EFRP.**

## O que aparece no painel

A tela **Painel de status** mostra a versão instalada do app, a versão do Android e seu nível de API, se o app é Device Owner e o resultado da consulta de suporte à EFRP. O botão **Atualizar** consulta os valores novamente.

A consulta EFRP só é feita depois que o app for Device Owner. Antes disso, o painel informa que é preciso tornar o app Device Owner. Como especificado, uma chamada bem-sucedida a `getFactoryResetProtectionPolicy(admin)` é mostrada como **EFRP: SUPORTADA**; `UnsupportedOperationException` aparece como **NÃO SUPORTADA**, e outra exceção aparece como **ERRO (nome)**.

**Importante:** consultar essa API não aplica uma política EFRP. A documentação do Android explica que o método lê a política atual e pode retornar `null` quando nenhuma política foi configurada. Portanto, **SUPORTADA não significa que a proteção já esteja ativa**. A aplicação da política ficou fora da Etapa 1 e deve ser feita na Etapa 8.

## Requisitos para compilar

- Windows com Android Studio instalado.
- JDK 21 selecionado para o Gradle no Android Studio.
- Android SDK Platform 36 instalado. Se o Android Studio pedir para instalar, aceite.
- Internet na primeira compilação, para o Gradle Wrapper baixar o Gradle 8.13 e o Gradle baixar as ferramentas necessárias.
- Tablet VAIO TL10 conectado por USB com Depuração USB ativada e autorizada.

O projeto usa Android Gradle Plugin 8.10.0, Kotlin 2.2.0 e Gradle Wrapper 8.13. O código compila para JVM 17 usando o DSL `kotlin { compilerOptions { ... } }`.

## Abrir o projeto e gerar o APK

1. Extraia o ZIP. A pasta do projeto chama-se `FreezeKit`.
2. No Android Studio, clique em **Open** e escolha a pasta `FreezeKit` extraída. Escolha a pasta raiz, onde estão `settings.gradle.kts` e `gradlew.bat`, e não a pasta `app`.
3. Aguarde a sincronização do Gradle. Se aparecer uma solicitação para instalar o Android SDK Platform 36, aceite-a.
4. Confira o JDK do Gradle em **File > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK**. Selecione um JDK 21.
5. No terminal do Android Studio, aberto na pasta raiz do projeto, execute:

   ```bat
   .\gradlew.bat assembleDebug
   ```

6. O APK de depuração será criado em:

   ```text
   app\build\outputs\apk\debug\app-debug.apk
   ```

## Instalar e testar

1. Confirme que o tablet está conectado:

   ```bat
   adb devices
   ```

   O número de série deve aparecer com o estado `device`. No tablet, aceite a autorização de depuração USB se ela aparecer.

2. Antes de registrar o Device Owner, confirme que o tablet está sem contas e tem apenas o usuário principal. O comando de registro pode ser recusado se o aparelho não estiver elegível ou já tiver outro proprietário.

3. Ainda na pasta raiz do projeto, instale o APK de teste:

   ```bat
   adb install -t -r app\build\outputs\apk\debug\app-debug.apk
   ```

   `-t` permite instalar o APK porque o Manifesto declara `android:testOnly="true"`; `-r` reinstala/atualiza se já houver uma cópia do mesmo app.

4. Abra o app **FreezeKit** no tablet. O painel deve mostrar a versão do app e as informações do Android, `Device Owner: NÃO` e que a consulta EFRP fica disponível após tornar o app Device Owner. Isso valida a Activity e a leitura de versão.

5. No terminal do computador, registre o receptor como Device Owner:

   ```bat
   adb shell dpm set-device-owner --user 0 freezekit.capitaldascestas/.AdminReceiver
   ```

   O resultado esperado é uma mensagem `Success` do Android. Se o comando for recusado, leia a mensagem: o estado de provisionamento do tablet pode não atender aos requisitos. Não faça uma redefinição de fábrica sem salvar o que for importante.

6. Abra o FreezeKit novamente ou toque em **Atualizar**. O painel deve mostrar `Device Owner: SIM` e executar a consulta EFRP. No VAIO TL10, a especificação registra o resultado esperado como suporte; o valor observado depende do retorno real do sistema.

7. Para remover o Device Owner durante os testes, execute:

   ```bat
   adb shell dpm remove-active-admin --user 0 freezekit.capitaldascestas/.AdminReceiver
   ```

   Depois, se quiser desinstalar o app:

   ```bat
   adb uninstall freezekit.capitaldascestas
   ```

## O que cada arquivo faz

- `settings.gradle.kts` nomeia o projeto e inclui o módulo `app`.
- `build.gradle.kts` define as versões do Android Gradle Plugin e do Kotlin.
- `gradle/wrapper/` e `gradlew.bat` permitem usar Gradle 8.13 sem instalá-lo manualmente.
- `app/build.gradle.kts` define pacote, SDKs, versão e alvo JVM do Kotlin.
- `app/src/main/AndroidManifest.xml` registra a tela inicial, `testOnly` e o receptor que o Android pode vincular como administrador.
- `app/src/main/res/xml/device_admin.xml` é o recurso de metadados do administrador. Não declara políticas de bloqueio.
- `AdminReceiver.kt` implementa o receptor Android necessário para registrar o app como Device Owner.
- `MainActivity.kt` monta o painel, lê a versão do app e do sistema, verifica Device Owner e consulta EFRP somente para o Device Owner.
- `app/src/main/res/values/` contém os textos e o tema visual básico.

## Escopo e APIs

A consulta `DevicePolicyManager.getFactoryResetProtectionPolicy(ComponentName)` é uma API pública documentada para obter a política de proteção contra redefinição. O projeto chama somente esse método de leitura; não chama `setFactoryResetProtectionPolicy`. A tela também usa `isDeviceOwnerApp`, `Build.VERSION` e APIs padrão da Activity.

Nesta etapa não são pedidas permissões de aplicativo e não são usadas bibliotecas externas no módulo `app`.

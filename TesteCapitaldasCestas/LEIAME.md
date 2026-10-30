# Teste Capital das Cestas

Projeto Android nativo de teste, em Kotlin, para validar o tablet e o ambiente ADB. Ele tem uma única tela com quatro informações e o botão **Atualizar**. O app não ativa modo quiosque, não aplica políticas de bloqueio, não usa internet e não solicita permissões de aplicativo.

## Requisitos do computador

- Android Studio estável com suporte ao Android SDK 36.
- Android SDK Platform 36 instalado pelo SDK Manager do Android Studio.
- Um tablet conectado ao computador com Depuração USB ativada e autorizado para ADB.
- O Gradle Wrapper baixa o Gradle 8.13 na primeira compilação; por isso, o computador precisa de internet nesse primeiro uso.

## Abrir o projeto e gerar o APK de depuração

1. Extraia o arquivo ZIP para uma pasta do computador.
2. No Android Studio, escolha **Open** e selecione a pasta `TesteCapitaldasCestas` extraída. Selecione a pasta raiz, onde estão `settings.gradle.kts` e `gradlew.bat`, e não a pasta `app`.
3. Aguarde a sincronização do Gradle. Se o Android Studio pedir para instalar o Android SDK Platform 36, aceite ou instale-o em **Tools > SDK Manager**.
4. Abra o terminal do Android Studio na pasta raiz do projeto e execute:

   ```bat
   .\gradlew.bat assembleDebug
   ```

   Também é possível gerar o APK pelo menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. O APK será criado em:

   ```text
   app\build\outputs\apk\debug\app-debug.apk
   ```

## Instalar no tablet

1. Conecte o tablet por USB, habilite a Depuração USB e aceite a solicitação de autorização que aparecer no tablet.
2. No terminal, confira a conexão:

   ```bat
   adb devices
   ```

3. Na pasta raiz do projeto, instale o APK de depuração:

   ```bat
   adb install -t -r app\build\outputs\apk\debug\app-debug.apk
   ```

   O parâmetro `-t` é obrigatório porque o manifesto contém `android:testOnly="true"`. O parâmetro `-r` permite reinstalar/atualizar a instalação existente.
4. Abra **Teste Capital das Cestas** no tablet. A tela mostra a versão do Android, o nível de API, o estado de Device Owner e a resposta do sistema à consulta de suporte à EFRP. Toque em **Atualizar** para consultar os valores novamente.

## Tornar o app Device Owner

No terminal do computador, execute:

```bat
adb shell dpm set-device-owner --user 0 com.capitaldascestas.teste/.AdminReceiver
```

O comando associa o receptor administrativo do app ao usuário 0 como Device Owner. O Android pode recusar a operação se o tablet já tiver outro Device Owner, tiver contas configuradas ou não estiver em um estado elegível para provisionamento. Siga a mensagem apresentada pelo próprio comando; não redefina o tablet sem antes salvar os dados importantes.

Depois de uma resposta de sucesso, abra o app ou toque em **Atualizar**. A linha **Device Owner** deve mudar para **SIM**. Este projeto não chama APIs para aplicar políticas de administração.

## Remover o Device Owner

Execute:

```bat
adb shell dpm remove-active-admin --user 0 com.capitaldascestas.teste/.AdminReceiver
```

Esse comando desativa o administrador e remove o estado de Device Owner. A opção é permitida para este app porque ele declara `android:testOnly="true"`. Para desinstalar o app depois, execute:

```bat
adb uninstall com.capitaldascestas.teste
```

## Incerteza sobre EFRP

**INCERTEZA:** não é possível garantir, sem consultar o VAIO TL10 real, que a linha de suporte à EFRP mostrará **SIM**. O app chama `DevicePolicyManager.isFactoryResetProtectionPolicySupported()` e exibe o resultado informado pelo sistema do tablet. O valor depende do suporte fornecido pelo fabricante e pela versão do sistema; o app apenas consulta esse suporte e não configura uma política de proteção.

## Configuração incluída

- `applicationId` e `namespace`: `com.capitaldascestas.teste`
- `minSdk`: 30
- `targetSdk`: 36
- `compileSdk`: 36
- Android Gradle Plugin: 8.10.0
- Kotlin Gradle Plugin: 2.2.0
- Gradle Wrapper: 8.13
- Sem bibliotecas externas no módulo do app.

# FreezeKit — Etapa 4 (versão 0.4)

FreezeKit é um navegador de quiosque Android para o CONFER. Esta entrega acrescenta entrada administrativa oculta por TOTP, recuperação, assistente de primeira abertura e painel de administração. O projeto parte do ZIP da Etapa 3 anexado e conserva o navegador, a política de URLs, o Device Owner e o Lock Task.

## Como sair do quiosque se algo der errado

Use somente em um tablet de teste conectado por USB a um computador que já esteja autorizado para Depuração USB. A variante continua marcada `android:testOnly="true"`.

```bat
adb devices
adb shell dpm remove-active-admin --user 0 freezekit.capitaldascestas/.AdminReceiver
```

O comando `dpm remove-active-admin` está documentado pelo Android para builds de teste e pode remover também um Device Owner. **INCERTEZA:** o firmware do fabricante pode tratar a saída de Lock Task e a preferência HOME de modo diferente; valide o procedimento no VAIO TL10 antes do piloto. Não conte com comandos de shell AOSP não documentados. Mantenha ADB/USB disponível durante os testes de quiosque.

## Abrir e compilar no Android Studio

Extraia o ZIP e abra a pasta raiz `FreezeKit` — a que contém `settings.gradle.kts`, `gradlew` e `gradlew.bat`. Use JDK 21 e instale Android SDK Platform 36 pelo SDK Manager. A primeira sincronização/build precisa de internet para obter Gradle, plugins e a dependência QR.

No terminal do Android Studio, a partir da raiz do projeto:

```bat
.\gradlew.bat assembleDebug
```

O APK de debug sai em `app\build\outputs\apk\debug\app-debug.apk`. O Wrapper Gradle 8.13 já está incluído. O projeto mantém `minSdk 30`, `compileSdk 36`, `targetSdk 36`, Java/Kotlin JVM 17, AGP 8.10.0 e Kotlin 2.2.0. A única dependência externa de runtime acrescentada é `com.google.zxing:core:3.5.4`, usada somente para desenhar o QR; TOTP, Base32 e PBKDF2 são implementados com APIs públicas do Android/Java.

No ambiente em que este ZIP foi gerado, `./gradlew help` e a resolução de `debugRuntimeClasspath` passaram; `assembleDebug` não pôde ser concluído porque não havia Android SDK configurado/Platform 36 instalada. O APK não está incluído; compile-o no Android Studio após instalar a plataforma indicada.

## Instalar/atualizar e provisionar

Atualize o tablet já Device Owner usando a mesma chave de assinatura/debug da versão instalada:

```bat
adb devices
adb install -t -r app\build\outputs\apk\debug\app-debug.apk
```

**Não execute `set-device-owner` novamente** se o tablet já é Device Owner. Em um tablet de teste elegível e limpo, o provisionamento inicial é:

```bat
adb shell dpm set-device-owner --user 0 freezekit.capitaldascestas/.AdminReceiver
```

O primeiro início como Device Owner abre o assistente. Para 12 tablets, configure o segredo TOTP e o código de recuperação compartilhados: no primeiro tablet, gere-os e copie-os para o cofre; nos demais, escolha a opção de digitar o valor existente.

## Como funciona

A entrada administrativa é **7 toques no canto superior direito (80×80 dp) em até 3 segundos**. O detector observa os eventos, chama o despacho normal uma única vez e retorna o resultado original; os toques continuam sendo entregues à WebView/CONFER. O sétimo toque abre a tela de autenticação.

O TOTP é calculado localmente: segredo aleatório de 20 bytes/160 bits, Base32 própria, HMAC-SHA1, seis dígitos, período de 30 segundos e tolerância de exatamente um passo anterior/seguinte. O passo TOTP aceito é guardado para evitar reutilização. O segredo persistido fica cifrado com AES-GCM sob uma chave não exportável do Android Keystore; SharedPreferences guarda somente IV e ciphertext/tag. O modo Recovery aceita 16 caracteres do alfabeto `A-Z` sem `I`/`O` e `2-9`; persiste apenas salt aleatório e PBKDF2-HMAC-SHA256 (100.000 iterações, 256 bits), com comparação de hash em tempo constante. Nenhum segredo/código é gravado em arquivo de log pelo app.

Se o Keystore não conseguir ler o TOTP, o app encaminha para Recovery e não regenera a chave automaticamente. Abrir a tela de reconfiguração não apaga dados; descartar registros ilegíveis exige uma sessão autenticada por Recovery e confirmação explícita.

Cinco falhas iniciam 30 segundos de espera; depois de cada espera, a próxima falha inicia uma duração dobrada. TOTP e Recovery compartilham o estado salvo localmente. O acesso correto zera o contador. **INCERTEZA:** a especificação não define teto operacional para a espera nem diz explicitamente se sucesso deve zerar a contagem; a implementação satura apenas no limite técnico de `Long` e interpreta sucesso como reset de erros consecutivos. O deadline usa relógio civil persistido para sobreviver a reinício; adiantar manualmente a hora pode encurtar a espera, e atrasá-la pode alongá-la. As restrições de hora automática pertencem à etapa futura M5/RF-40 e não são aplicadas aqui.

A sessão administrativa expira após dez minutos sem toque ou imediatamente em `ACTION_SCREEN_OFF`. Ela usa relógio monotônico em memória; reinício do processo encerra a sessão. O assistente de primeira abertura tem cinco passos visíveis: TOTP, Recovery, senha Android, teste do CONFER e ativação do quiosque. Há um slot de código reservado entre Recovery e senha para a futura etapa EFRP, mas nesta entrega ele é pulado: **nenhuma política EFRP é aplicada e nenhuma tela EFRP é exibida no assistente**.

No painel de administrador, é possível ver versão do app, Android/API, Device Owner, status de EFRP somente leitura, Lock Task, hora e tempo restante; editar URL inicial, hosts liberados e hosts de câmera; reconfigurar TOTP/Recovery; limpar cookies, Web Storage e cache do WebView; abrir Wi‑Fi e a tela de senha Android; entrar/sair do quiosque; ou voltar ao CONFER. O formulário só salva URL inicial HTTPS válida, sem usuário/senha, na porta padrão 443 e com host listado. A política herdada do navegador valida esquema HTTPS e host exato para a navegação principal, sem wildcard/subdomínios implícitos. **INCERTEZA:** os subrecursos/iframes incorporados pela própria página CONFER não são filtrados por uma lista completa de host; confirme no tablet quais CDNs/APIs precisam carregar antes de ampliar a regra para não quebrar o site.

Para Wi‑Fi e senha, o app resolve a Activity em tempo de execução, não fixa `com.android.settings`; se a resolução estiver ausente ou ambígua, não abre. Durante o Lock Task, autoriza temporariamente o pacote resolvido e, ao voltar ao FreezeKit ou expirar a sessão, restaura a allowlist para somente FreezeKit. **INCERTEZA:** a resolução e a tela de senha podem variar por fabricante; a intenção pública para senha é `DevicePolicyManager.ACTION_SET_NEW_PASSWORD` (não existe como API pública `Settings.ACTION_SET_NEW_PASSWORD`). O estado de senha é conferido ao voltar com `KeyguardManager.isDeviceSecure()`.

Os botões de manutenção e registro de eventos aparecem desativados como espaço reservado. A Activity de status provisória, os botões DEV/teste Google/subdomínio e a entrada antiga `ACTION_REQUEST_URL`/extras foram removidos. A saída normal do quiosque consulta `MAIN + HOME`, escolhe um launcher cujo pacote não seja FreezeKit e o inicia como Intent explícito.

## Roteiro de teste — itens 1 a 10

| Item | Como testar |
|---|---|
| 1. Gesto | Abra o CONFER; dê sete toques curtos no canto superior direito dentro de três segundos. Confirme que cada toque ainda é recebido pela página e que só depois aparece a tela de código. Faça também seis toques, toque fora da área, mova o dedo e use dois dedos; nenhum desses casos deve abrir a tela. |
| 2. TOTP | Configure no Authy o QR emitido e valide um código atual. Teste no limite da virada de período e confirme que um código de dois minutos atrás é recusado. Confirme que o mesmo passo aceito não pode ser reutilizado. |
| 3. Segredo cifrado | Gere um segredo; confira QR + Base32 e valide. Feche/reabra o app: o QR/texto não deve reaparecer sozinho. Inspecione SharedPreferences: o TOTP deve ter IV/ciphertext/tag, nunca texto puro (flags/contador e salt/hash de Recovery são outros campos). Force uma falha de Keystore: o app deve pedir Recovery; abrir reconfiguração não apaga nada, e o descarte só ocorre após Recovery + confirmação explícita. |
| 4. Recovery | Gere o código e salve-o no cofre; confirme 16 caracteres, grupos 4×4, alfabeto sem `I`/`O` e que ele não reaparece após recriação. Faça login pelo código. Inspecione preferências e confirme salt/hash, sem texto puro. Em reconfiguração, confirme o diálogo antes de substituir um código existente. |
| 5. Tentativas | Erre cinco vezes entre TOTP e Recovery: a quinta inicia espera de 30 s. Reinicie o processo e confirme que a espera continua. Depois do prazo, erre uma vez: deve iniciar 60 s; depois 120 s. Um login válido deve limpar o estado. Verifique que nenhum caminho chama wipe/reset do dispositivo. |
| 6. Sessão | Entre no painel e não toque por dez minutos; deve voltar ao CONFER. Repita e apague a tela: deve encerrar imediatamente. Teste um toque depois do prazo e confirme que a sessão não revive. Abra Wi‑Fi, deixe a tela externa aberta até expirar e confirme que o pacote externo sai da allowlist. |
| 7. Primeira abertura | Como Device Owner sem configuração, complete TOTP, Recovery, senha (ou “já configurada”), CONFER (Funcionou) e conclusão. Cancele a tela de senha e marque CONFER como “Não funcionou”; o assistente deve permanecer no passo pendente. Confirme que nenhum passo/política EFRP aparece. |
| 8. Funções admin | Abra pelo gesto e valide os dados do painel. Salve URL inválida, HTTP, host não permitido ou wildcard: nada deve ser salvo. Salve configuração HTTPS válida, reconfigure TOTP/Recovery e aceite os diálogos antes de substituir dados existentes. Limpe cookies, Web Storage e cache; confirme no VAIO que o CONFER carrega todos os subrecursos necessários. EFRP deve ser só leitura. |
| 9. Wi‑Fi/senha | Durante Lock Task, abra Wi‑Fi e senha Android. Confirme que a tela abre quando há exatamente um handler seguro, que o retorno restaura somente FreezeKit na allowlist e que a senha é verificada com `isDeviceSecure()`. Teste explicitamente no VAIO TL10/API 36. |
| 10. Remoção do provisório | Pesquise o projeto: não deve existir `StatusActivity`, botões DEV/Google/subdomínio, `ACTION_REQUEST_URL` nem extra de URL solicitado. A WebView e a política HTTPS/host exato devem continuar funcionando; não deve haver chamada de escrita EFRP, M5, manutenção ou log de eventos. |

## Fora do escopo e decisões incertas

Esta etapa **não** aplica `setFactoryResetProtectionPolicy`, não implementa M5 (RF-19 a RF-30/RF-40), manutenção (Etapa 6), registro de eventos (Etapa 5) ou instalação/remoção de Device Owner de produção. A consulta EFRP no painel usa somente `getFactoryResetProtectionPolicy` e mostra política ativa/inativa/ausente, não suportada ou erro.

**INCERTEZA:** `otpauth://` é convenção de interoperabilidade, não uma RFC final; valide o QR no autenticador usado pela equipe. Os parâmetros seguem exatamente SHA1/6 dígitos/30 s. **INCERTEZA:** `ACTION_SET_NEW_PASSWORD` e o retorno do sistema variam por fabricante; o app não assume `RESULT_OK`, e continua no assistente se `isDeviceSecure()` ainda for falso. **INCERTEZA:** a allowlist Lock Task é por pacote; durante a curta janela autorizada, outras Activities do mesmo pacote externo podem ficar acessíveis. **INCERTEZA:** a tela específica de Wi‑Fi/senha e a resolução de handlers precisam ser testadas no dispositivo piloto. **INCERTEZA:** o `KioskManager` aplica várias políticas DPM em sequência; uma falha de firmware entre chamadas pode deixar estado parcial. Teste entrada, saída, retorno do launcher e reinício do processo no VAIO, mantendo ADB/USB disponível.

## Fontes públicas

- [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec), [Cipher](https://developer.android.com/reference/javax/crypto/Cipher) e [SharedPreferences](https://developer.android.com/reference/android/content/SharedPreferences).
- [Activity.dispatchTouchEvent](https://developer.android.com/reference/android/app/Activity#dispatchTouchEvent(android.view.MotionEvent)), [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent) e [Lock Task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
- [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager), [AÇÃO pública de senha](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#ACTION_SET_NEW_PASSWORD), [KeyguardManager.isDeviceSecure](https://developer.android.com/reference/android/app/KeyguardManager#isDeviceSecure()) e [Settings.ACTION_WIFI_SETTINGS](https://developer.android.com/reference/android/provider/Settings#ACTION_WIFI_SETTINGS).
- [WebView](https://developer.android.com/reference/android/webkit/WebView), [CookieManager](https://developer.android.com/reference/android/webkit/CookieManager), [WebStorage](https://developer.android.com/reference/android/webkit/WebStorage), [Package visibility](https://developer.android.com/training/package-visibility) e [ADB/DPM](https://developer.android.com/tools/adb).
- [RFC 6238 (TOTP)](https://www.rfc-editor.org/rfc/rfc6238), [RFC 4226 (HOTP)](https://www.rfc-editor.org/rfc/rfc4226), [RFC 4648 (Base32)](https://www.rfc-editor.org/rfc/rfc4648) e [RFC 8018 (PBKDF2)](https://www.rfc-editor.org/rfc/rfc8018.html).
- [ZXing Core 3.5.4 no Maven Central](https://repo1.maven.org/maven2/com/google/zxing/core/3.5.4/) e [release correspondente](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4).

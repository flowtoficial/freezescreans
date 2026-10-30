# Plano de Implantação do Programa de Bloqueio

Do zero aos 12 tablets rodando · Capital das Cestas · Versão 1.0 · 29/10/2026

| Item | Definição |
|---|---|
| Documento | Plano de implantação — o passo a passo do zero até o programa rodando nos 12 tablets |
| Documento de apoio | Especificação do Programa de Bloqueio dos Tablets (v1.0) — referência dos IDs RF-xx |
| Responsável | Gabriel (planejamento, desenvolvimento com o Manus e instalação) |
| Aprovação final | Gabriel e o dono da empresa |
| Escopo | 1 tablet piloto → 11 tablets restantes · VAIO TL10 · Android 16 |

# Visão geral: o mapa das 7 fases

Cada fase termina em um **portão**: uma lista curta de condições que precisam ser verdadeiras para seguir. Não pule portões. Eles existem para que um erro seja barato de corrigir.

| Fase | Nome | O que acontece | Portão de saída |
|---|---|---|---|
| 0 | Decisões e preparação | Fechar decisões pendentes, criar contas, comprar materiais. | Todas as decisões registradas; Gmail da EFRP pronto. |
| 1 | Ambiente de desenvolvimento | Instalar ferramentas, criar repositório e chave de assinatura. | Um app de teste instala no tablet pelo cabo. |
| 2 | Viabilidade e especificação v1.1 | Validar o VAIO e completar as lacunas da especificação. | Nenhum bloqueio técnico encontrado; v1.1 publicada. |
| 3 | Desenvolvimento | Construir o app em 8 etapas, testando cada uma no piloto. | Todos os 38 requisitos atendidos no piloto. |
| 4 | Piloto | Testes de burla, teste da EFRP e uso real por um conferente. | Nenhuma burla funciona; aprovação do dono. |
| 5 | Implantação | Instalar e entregar os 11 tablets restantes. | 12 tablets em operação, inventário completo. |
| 6 | Operação | Rotinas de manutenção e acompanhamento. | Contínua. |

<callout icon="⚠️" color="yellow_bg">
	**Caminho crítico (o que mais pode atrasar)**
	- **Fase 2:** se o VAIO não suportar a EFRP ou a câmera no navegador interno, o projeto muda de rumo. Por isso a viabilidade vem antes do desenvolvimento completo.
	- **Fase 3, etapas 6 e 7:** os bloqueios só entram depois do modo manutenção funcionar. Inverter essa ordem pode obrigar a resetar o tablet a cada erro.
	- **Fase 4:** o teste da EFRP apaga o piloto. Ele fica para o final, com tudo já salvo.
</callout>

# Como usar este plano

- Marque a caixa "Concluído" dentro de cada toggle quando a tarefa estiver feita.
- Preencha o campo **Prazo** de cada toggle com uma data antes de começar cada fase, e combine essas datas com o dono da empresa. Um prazo combinado com outra pessoa é mais difícil de adiar.
- Os códigos **RF-xx** são os requisitos da Especificação. Cite-os ao pedir cada etapa ao Manus.
- Os códigos **T-xx** (tarefas) e **TB-xx** (testes de burla) servem para registrar problemas: "TB-07 falhou no tablet 03".
- Senhas, PINs e chaves **nunca** são escritos neste documento. Eles ficam no gerenciador de senhas (Anexo A).

# Fase 0 · Decisões e preparação
<callout icon="🎯" color="gray_bg">
	**Objetivo**
	Fechar tudo o que ainda está em aberto e deixar contas e materiais prontos.
	
	**Entrega:** Decisões registradas, gerenciador de senhas configurado, Gmail da EFRP pronto.
</callout>

## 0.1 · Decisões pendentes

*Cada decisão abaixo muda o que será pedido ao Manus. A coluna "Sugestão" é a recomendação do planejamento.*

<details>
<summary>D-01 · Tela ligada enquanto o tablet carrega?</summary>
	- [ ] Decidido
	**Decisão tomada:** 
	**Opções:** Sempre ligada na tomada / apaga normalmente
	**Sugestão:** Apagar normalmente: economiza tela e bateria, e a senha do Android protege o tablet parado.
</details>
<details>
<summary>D-02 · Configuração alterada tablet a tablet é aceitável?</summary>
	- [ ] Decidido
	**Decisão tomada:** 
	**Opções:** Sim (sem servidor) / Não (exige servidor, fora do escopo)
	**Sugestão:** Sim. Mudanças de endereço e PIN devem ser raras.
</details>
<details>
<summary>D-03 · Procedimento de PIN esquecido</summary>
	- [ ] Decidido
	**Decisão tomada:** 
	**Opções:** Reset pelos botões + Gmail da EFRP + reinstalação
	**Sugestão:** Aceitar e documentar (ver Contingência C-02). Por isso o PIN fica no gerenciador de senhas.
</details>
<details>
<summary>D-04 · Confirmar valores padrão da Especificação</summary>
	- [ ] Decidido
	**Decisão tomada:** 
	**Opções:** Gesto, tempos, janela de atualização, retenção do registro
	**Sugestão:** Manter os valores da seção 8 da Especificação.
</details>
<details>
<summary>D-05 · Nome do pacote do app</summary>
	- [ ] Decidido
	**Decisão tomada:** 
	**Opções:** `com.capitaldascestas.quiosque` ou outro
	**Sugestão:** Confirmar antes da Fase 3. Não muda depois da instalação.
</details>
<details>
<summary>D-06 · Computador de desenvolvimento</summary>
	- [ ] Decidido
	**Decisão tomada:** 
	**Opções:** Windows / macOS / Linux
	**Sugestão:** Usar sempre o mesmo computador; registrar qual é.
</details>

## 0.2 · Contas e cofre de credenciais

<details>
<summary>T-01 · Escolher o gerenciador de senhas compartilhado (você e o dono).</summary>
	- [ ] Concluído
	**Como fazer:** Qualquer gerenciador com cofre compartilhado. Criar o cofre "Tablets CONFER" (Anexo A).
	**Prazo:** 
</details>
<details>
<summary>T-02 · Separar o Gmail livre que será a conta da EFRP.</summary>
	- [ ] Concluído
	**Como fazer:** Usar um Gmail exclusivo para isso, que não seja pessoal de ninguém.
	**Prazo:** 
</details>
<details>
<summary>T-03 · Proteger o Gmail da EFRP.</summary>
	- [ ] Concluído
	**Como fazer:** Ativar a verificação em 2 etapas; cadastrar e-mail e telefone de recuperação da empresa; salvar no cofre.
	**Prazo:** 
</details>
<details>
<summary>T-04 · Obter o ID numérico desse Gmail (21 dígitos).</summary>
	- [ ] Concluído
	**Como fazer:** Logado nesse Gmail, abrir a página da People API (método `people.get`), usar "Try this API" com `resourceName = people/me` e `personFields = metadata`. Copiar o número de `metadata.sources[].id` para o cofre.
	**Prazo:** 
</details>
<details>
<summary>T-05 · Criar o repositório privado do código.</summary>
	- [ ] Concluído
	**Como fazer:** Ex.: GitHub privado, com verificação em 2 etapas na conta.
	**Prazo:** 
</details>

## 0.3 · Materiais e pessoas

<details>
<summary>T-06 · Separar o tablet piloto.</summary>
	- [ ] Concluído
	**Como fazer:** Um VAIO TL10 novo. Anotar o número de série.
	**Prazo:** 
</details>
<details>
<summary>T-07 · Cabo USB de dados e computador.</summary>
	- [ ] Concluído
	**Como fazer:** Confirmar que o cabo transfere dados (não só carrega).
	**Prazo:** 
</details>
<details>
<summary>T-08 · Orçar e comprar os cases com trava.</summary>
	- [ ] Concluído
	**Como fazer:** Modelo que cubra os botões físicos e prenda à bancada. Comprar 1 para o piloto; os outros 11 após o portão da Fase 4.
	**Prazo:** 
</details>
<details>
<summary>T-09 · Etiquetas patrimoniais.</summary>
	- [ ] Concluído
	**Como fazer:** Numeração sugerida: CC-TAB-01 a CC-TAB-12.
	**Prazo:** 
</details>
<details>
<summary>T-10 · Preparar a rede Wi-Fi dos tablets.</summary>
	- [ ] Concluído
	**Como fazer:** Rede exclusiva, WPA2/WPA3, senha no cofre.
	**Prazo:** 
</details>
<details>
<summary>T-11 · Redigir o termo de responsabilidade e a política de uso.</summary>
	- [ ] Concluído
	**Como fazer:** Incluir: o tablet é de uso exclusivo do CONFER, a câmera serve só para leitura de código, e danos ou tentativas de burla são de responsabilidade de quem estiver com o aparelho.
	**Prazo:** 
</details>

<callout icon="🚦" color="blue_bg">
	**PORTÃO · saída da Fase 0**
	- [ ] D-01 a D-06 decididas e registradas.
	- [ ] Cofre criado, com o Gmail da EFRP protegido e o ID numérico salvo.
	- [ ] Tablet piloto, cabo, rede Wi-Fi e repositório prontos.
</callout>

# Fase 1 · Ambiente de desenvolvimento
<callout icon="🎯" color="gray_bg">
	**Objetivo**
	Ter no computador tudo o que transforma o código do Manus em um app instalado no tablet.
	
	**Entrega:** Um app de teste instalado no tablet piloto pelo cabo USB.
</callout>

## 1.1 · Ferramentas

<details>
<summary>T-12 · Instalar o Android Studio.</summary>
	- [ ] Concluído
	**Como fazer:** Baixar do site oficial do Android. Ele já traz o Java e o gerenciador de SDK.
	**Prazo:** 
</details>
<details>
<summary>T-13 · Instalar o SDK do Android 16 e as Platform Tools.</summary>
	- [ ] Concluído
	**Como fazer:** No Android Studio: SDK Manager → Android 16 (API 36) e "Android SDK Platform-Tools" (contém o ADB).
	**Prazo:** 
</details>
<details>
<summary>T-14 · Deixar o ADB acessível no terminal.</summary>
	- [ ] Concluído
	**Como fazer:** Adicionar a pasta `platform-tools` ao PATH. Testar com `adb version`.
	**Prazo:** 
</details>
<details>
<summary>T-15 · Instalar o Git e clonar o repositório.</summary>
	- [ ] Concluído
	**Como fazer:** Testar um envio (push) de um arquivo qualquer.
	**Prazo:** 
</details>
<details>
<summary>T-16 · Definir como o Manus entrega o código.</summary>
	- [ ] Concluído
	**Como fazer:** Confirmar se o Manus gera o projeto Android completo (pasta com Gradle) para você abrir no Android Studio, ou se só entrega trechos. O fluxo deste plano assume o projeto completo.
	**Prazo:** 
</details>

## 1.2 · Chave de assinatura (crítico)

<details>
<summary>T-17 · Gerar a chave de assinatura de produção.</summary>
	- [ ] Concluído
	**Como fazer:** Android Studio → Build → Generate Signed App Bundle / APK → APK → Create new. Validade de 25 anos ou mais.
	**Prazo:** 
</details>
<details>
<summary>T-18 · Guardar a chave em dois lugares.</summary>
	- [ ] Concluído
	**Como fazer:** Arquivo `.jks`, senha da chave e senha do arquivo: no cofre e em um backup offline (pendrive guardado).
	**Prazo:** 
</details>
<details>
<summary>T-19 · Garantir que a chave nunca vá para o repositório.</summary>
	- [ ] Concluído
	**Como fazer:** Adicionar `*.jks` e `keystore.properties` ao `.gitignore`.
	**Prazo:** 
</details>

<callout icon="🛑" color="red_bg">
	**Por que a chave é crítica**
	O Android só aceita atualizar um app se a nova versão tiver a mesma assinatura. Sem a chave original, a única forma de atualizar os 12 tablets é resetar todos e reinstalar do zero.
</callout>

## 1.3 · Primeira conexão com o tablet piloto

<details>
<summary>T-20 · Ligar o tablet e fazer a configuração inicial **sem** conta Google.</summary>
	- [ ] Concluído
	**Como fazer:** Conectar ao Wi-Fi dos tablets e pular todas as telas de conta.
	**Prazo:** 
</details>
<details>
<summary>T-21 · Ativar as opções do desenvolvedor.</summary>
	- [ ] Concluído
	**Como fazer:** Configurações → Sobre o tablet → tocar 7 vezes em "Número da versão".
	**Prazo:** 
</details>
<details>
<summary>T-22 · Ativar a depuração USB e autorizar o computador.</summary>
	- [ ] Concluído
	**Como fazer:** Opções do desenvolvedor → Depuração USB. Conectar o cabo e aceitar a chave RSA no tablet.
	**Prazo:** 
</details>
<details>
<summary>T-23 · Confirmar a conexão.</summary>
	- [ ] Concluído
	**Como fazer:** `adb devices` deve listar o tablet como `device`.
	**Prazo:** 
</details>
<details>
<summary>T-24 · Instalar um app de teste.</summary>
	- [ ] Concluído
	**Como fazer:** Pedir ao Manus um app mínimo ("Olá, Capital das Cestas"), compilar e instalar com `adb install`.
	**Prazo:** 
</details>

<callout icon="🚦" color="blue_bg">
	**PORTÃO · saída da Fase 1**
	- [ ] Android Studio, SDK, ADB e Git funcionando.
	- [ ] Chave de assinatura gerada e guardada em dois lugares.
	- [ ] App de teste instalado e aberto no tablet piloto.
</callout>

# Fase 2 · Viabilidade e especificação v1.1
<callout icon="🎯" color="gray_bg">
	**Objetivo**
	Confirmar que o VAIO suporta o que o projeto exige e fechar as lacunas da especificação.
	
	**Entrega:** Relatório de viabilidade e Especificação v1.1.
</callout>

## 2.1 · Testes de viabilidade no VAIO

<details>
<summary>V-01 · Serviços do Google presentes</summary>
	- [ ] Concluído
	**Como fazer:** Configurações → Apps → mostrar apps do sistema → procurar "Google Play Services".
	**Se falhar:** A EFRP não funciona. Proteção contra furto fica só física; avisar o dono.
</details>
<details>
<summary>V-02 · Câmera no CONFER</summary>
	- [ ] Concluído
	**Como fazer:** Abrir o CONFER no Chrome do tablet e ler um código.
	**Se falhar:** Investigar com o Manus antes de seguir; pode ser permissão ou o próprio CONFER.
</details>
<details>
<summary>V-03 · Device Owner aceito</summary>
	- [ ] Concluído
	**Como fazer:** Com o app de teste, rodar `adb shell dpm set-device-owner` (o Manus inclui o receptor de administração no app de teste).
	**Se falhar:** Ver o erro exibido; geralmente é conta logada ou outro usuário no tablet.
</details>
<details>
<summary>V-04 · Suporte à EFRP</summary>
	- [ ] Concluído
	**Como fazer:** O app de teste mostra o resultado de `isFactoryResetProtectionPolicySupported()`.
	**Se falhar:** Igual ao V-01.
</details>
<details>
<summary>V-05 · Remoção do Device Owner</summary>
	- [ ] Concluído
	**Como fazer:** Com o app marcado como `testOnly` (versão de teste), rodar `adb shell dpm remove-active-admin`.
	**Se falhar:** Exige reset a cada reinstalação; desenvolvimento fica mais lento.
</details>

*Após os testes, voltar o tablet ao estado limpo: remover o Device Owner (V-05) ou resetar pelas Configurações.*

## 2.2 · Completar a especificação (v1.1)

<details>
<summary>T-25 · Corrigir a inconsistência do Wi-Fi (RF-25).</summary>
	- [ ] Concluído
	**Como fazer:** Registrar que o modo administrador suspende `DISALLOW_CONFIG_WIFI` ao abrir o Wi-Fi e reaplica ao voltar.
	**Prazo:** 
</details>
<details>
<summary>T-26 · Registrar as decisões D-01 a D-06.</summary>
	- [ ] Concluído
	**Como fazer:** Incluir na seção de decisões e na configuração padrão.
	**Prazo:** 
</details>
<details>
<summary>T-27 · Definir o fluxo da primeira abertura.</summary>
	- [ ] Concluído
	**Como fazer:** Ordem: 1) criar o PIN (duas vezes); 2) informar o ID do Gmail da EFRP; 3) mostrar se a EFRP foi aplicada; 4) pedir a senha do Android do tablet; 5) testar o CONFER; 6) entrar no quiosque.
	**Prazo:** 
</details>
<details>
<summary>T-28 · Descrever as telas.</summary>
	- [ ] Concluído
	**Como fazer:** PIN (teclado numérico, tentativas restantes); administrador (botões das funções do RF-18 e status da EFRP); sem conexão (aviso, nova tentativa automática, sem mostrar endereços); primeira abertura (T-27).
	**Prazo:** 
</details>
<details>
<summary>T-29 · Definir o tratamento de erros.</summary>
	- [ ] Concluído
	**Como fazer:** Endereço inválido não pode ser salvo; falha do navegador interno (`onRenderProcessGone`) recarrega a página; tablet reiniciado no modo manutenção volta bloqueado.
	**Prazo:** 
</details>
<details>
<summary>T-30 · Publicar a Especificação v1.1.</summary>
	- [ ] Concluído
	**Como fazer:** Gerar o PDF e guardar no repositório.
	**Prazo:** 
</details>

<callout icon="🚦" color="blue_bg">
	**PORTÃO · saída da Fase 2**
	- [ ] V-01 a V-05 aprovados, ou riscos aceitos pelo dono por escrito.
	- [ ] Especificação v1.1 publicada no repositório.
	- [ ] Tablet piloto em estado limpo, sem Device Owner.
</callout>

# Fase 3 · Desenvolvimento (8 etapas)
<callout icon="🎯" color="gray_bg">
	**Objetivo**
	Construir o app em partes pequenas, testando cada uma no tablet piloto antes da próxima.
	
	**Entrega:** App completo (versão de teste) atendendo os 38 requisitos no piloto.
</callout>

## 3.1 · Ciclo de cada etapa

Repetir este ciclo em todas as etapas:

<details>
<summary>Passo 1 · Pedir</summary>
	Enviar ao Manus a Especificação v1.1 e pedir só a etapa atual, citando os RF-xx dela.
</details>
<details>
<summary>Passo 2 · Revisar</summary>
	Pedir ao Manus que explique em linguagem simples o que foi feito e como testar.
</details>
<details>
<summary>Passo 3 · Compilar</summary>
	Abrir no Android Studio, compilar a versão de teste (`testOnly`).
</details>
<details>
<summary>Passo 4 · Instalar</summary>
	`adb install -r app-debug.apk` no tablet piloto.
</details>
<details>
<summary>Passo 5 · Testar</summary>
	Conferir cada critério de aceite dos RF-xx da etapa. Anotar falhas com o ID.
</details>
<details>
<summary>Passo 6 · Corrigir</summary>
	Voltar ao Manus com as falhas. Repetir até todos os critérios passarem.
</details>
<details>
<summary>Passo 7 · Salvar</summary>
	Enviar para o repositório com uma mensagem clara (ex.: "Etapa 3 concluída: RF-03 a RF-07").
</details>

## 3.2 · Etapas

<details>
<summary>Etapa 1 · Projeto base + Device Owner + status da EFRP</summary>
	- [ ] Concluído
	**Requisitos:** RF-01, RF-02, RF-32
	**Teste-chave:** Device Owner registrado; tela mostra se a EFRP é suportada.
	**Prazo:** 
</details>
<details>
<summary>Etapa 2 · Navegador interno: CONFER, lista de endereços e câmera</summary>
	- [ ] Concluído
	**Requisitos:** RF-08 a RF-13
	**Teste-chave:** Leitura de código funciona; outros sites e outros *.manus.space bloqueados.
	**Prazo:** 
</details>
<details>
<summary>Etapa 3 · Quiosque: tela inicial fixa e travamento</summary>
	- [ ] Concluído
	**Requisitos:** RF-03 a RF-07
	**Teste-chave:** Desbloquear o tablet abre o CONFER; botões do sistema não funcionam.
	**Prazo:** 
</details>
<details>
<summary>Etapa 4 · Modo administrador, PIN e primeira abertura</summary>
	- [ ] Concluído
	**Requisitos:** RF-14 a RF-18 + T-27
	**Teste-chave:** Gesto + PIN abrem o modo; 5 erros bloqueiam; expira em 10 min.
	**Prazo:** 
</details>
<details>
<summary>Etapa 5 · Registro de eventos</summary>
	- [ ] Concluído
	**Requisitos:** RF-37, RF-38
	**Teste-chave:** Uso do PIN e reinícios aparecem no registro.
	**Prazo:** 
</details>
<details>
<summary>Etapa 6 · Manutenção</summary>
	- [ ] Concluído
	**Requisitos:** RF-33 a RF-36
	**Teste-chave:** Modo manutenção libera o cabo; atualização por APK preserva a configuração; remoção do Device Owner funciona.
	**Prazo:** 
</details>
<details>
<summary>Etapa 7 · Bloqueios do sistema</summary>
	- [ ] Concluído
	**Requisitos:** RF-19 a RF-30
	**Teste-chave:** Cada bloqueio falha ao ser tentado; o modo manutenção continua funcionando.
	**Prazo:** 
</details>
<details>
<summary>Etapa 8 · EFRP</summary>
	- [ ] Concluído
	**Requisitos:** RF-31
	**Teste-chave:** Política aplicada (o teste real fica na Fase 4).
	**Prazo:** 
</details>

<callout icon="🛑" color="red_bg">
	**Regra de segurança da Fase 3**
	- Não começar a etapa 7 sem a etapa 6 aprovada no tablet.
	- Durante todo o desenvolvimento, usar a versão de teste (`testOnly`): ela permite remover o Device Owner pelo cabo sem resetar o tablet.
	- Se o tablet ficar travado sem saída, aplicar a Contingência C-04.
</callout>

## 3.3 · Versão final

<details>
<summary>T-31 · Gerar a versão final assinada.</summary>
	- [ ] Concluído
	**Como fazer:** Android Studio → Generate Signed APK com a chave da T-17, **sem** `testOnly`. Número de versão 1.0.0.
	**Prazo:** 
</details>
<details>
<summary>T-32 · Guardar o APK final.</summary>
	- [ ] Concluído
	**Como fazer:** No repositório (seção de versões) e no cofre de arquivos, com a data.
	**Prazo:** 
</details>
<details>
<summary>T-33 · Reinstalar o piloto do zero com a versão final.</summary>
	- [ ] Concluído
	**Como fazer:** Reset pelas Configurações (antes da EFRP) → instalação conforme o Anexo C.
	**Prazo:** 
</details>

<callout icon="🚦" color="blue_bg">
	**PORTÃO · saída da Fase 3**
	- [ ] As 8 etapas aprovadas no piloto.
	- [ ] APK final 1.0.0 assinado e guardado.
	- [ ] Piloto reinstalado do zero com a versão final, seguindo o Anexo C sem improviso.
</callout>

# Fase 4 · Piloto e testes de burla
<callout icon="🎯" color="gray_bg">
	**Objetivo**
	Provar que ninguém consegue sair do quiosque e que o tablet funciona no uso real.
	
	**Entrega:** Relatório do piloto e aprovação do dono para replicar.
</callout>

## 4.1 · Testes de burla

Faça cada teste como se fosse um colaborador tentando sair do CONFER. Todos devem **falhar** (o bloqueio vence).

<details>
<summary>TB-01 · Botões de início, voltar e recentes; gestos de navegação</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Nada sai do CONFER
</details>
<details>
<summary>TB-02 · Puxar a barra de notificações</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Barra não abre
</details>
<details>
<summary>TB-03 · Segurar o botão de energia</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Sem menu de desligar/reiniciar
</details>
<details>
<summary>TB-04 · Reiniciar em modo de segurança (combinação de botões)</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Não entra; ou, se entrar, case cobre os botões
</details>
<details>
<summary>TB-05 · Procurar reset nas Configurações (via qualquer caminho)</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Configurações inacessíveis; reset bloqueado
</details>
<details>
<summary>TB-06 · Conectar o cabo USB a um computador</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Sem arquivos e sem ADB
</details>
<details>
<summary>TB-07 · Digitar outro site / abrir link externo</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Não abre
</details>
<details>
<summary>TB-08 · Abrir outro app do domínio manus.space</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Não abre
</details>
<details>
<summary>TB-09 · Usar a câmera no Preço de Banana</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Câmera negada
</details>
<details>
<summary>TB-10 · Pressão longa em links e textos; tentar baixar arquivo</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Sem menu e sem download
</details>
<details>
<summary>TB-11 · Pelo teclado: configurações do teclado, pesquisa, emojis com links, ditado por voz</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Nada abre fora do CONFER
</details>
<details>
<summary>TB-12 · Atalhos de acessibilidade (segurar os dois botões de volume)</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Sem efeito ou sem saída do CONFER
</details>
<details>
<summary>TB-13 · Tentar o PIN 10 vezes</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Bloqueio crescente; eventos no registro
</details>
<details>
<summary>TB-14 · Deixar o modo administrador aberto e sair</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Volta ao quiosque em 10 min ou ao apagar a tela
</details>
<details>
<summary>TB-15 · Desligar e religar o Wi-Fi do roteador</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Tela de sem conexão; volta sozinho
</details>
<details>
<summary>TB-16 · Reiniciar o tablet (tirando bateria ou deixando descarregar)</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Pede a senha do Android e volta ao CONFER
</details>
<details>
<summary>TB-17 · Capturar a tela</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Bloqueado
</details>
<details>
<summary>TB-18 · Ativar Bluetooth ou trocar a rede Wi-Fi</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Bloqueado
</details>
<details>
<summary>TB-19 · Mudar data e hora</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Bloqueado
</details>
<details>
<summary>TB-20 · Ativar o modo manutenção e reiniciar o tablet</summary>
	- [ ] Aprovado (o bloqueio venceu)
	**Resultado esperado:** Volta com todos os bloqueios
</details>

## 4.2 · Uso real

<details>
<summary>T-34 · Instalar o case com trava e a etiqueta CC-TAB-01.</summary>
	- [ ] Concluído
	**Como fazer:** Registrar no inventário (Anexo D).
	**Prazo:** 
</details>
<details>
<summary>T-35 · Colocar o piloto em uso por um conferente.</summary>
	- [ ] Concluído
	**Como fazer:** Sugestão: 5 dias úteis em operação normal.
	**Prazo:** 
</details>
<details>
<summary>T-36 · Coletar a opinião do conferente.</summary>
	- [ ] Concluído
	**Como fazer:** Dificuldades com a senha, a câmera, a velocidade e a tela de sem conexão.
	**Prazo:** 
</details>
<details>
<summary>T-37 · Ler o registro de eventos ao final.</summary>
	- [ ] Concluído
	**Como fazer:** Procurar travamentos, quedas e tentativas de PIN.
	**Prazo:** 
</details>

## 4.3 · Teste destrutivo da EFRP (último passo do piloto)

<details>
<summary>T-38 · Confirmar o acesso ao Gmail da EFRP.</summary>
	- [ ] Concluído
	**Como fazer:** Entrar na conta e confirmar que a senha não mudou nas últimas 72 horas.
	**Prazo:** 
</details>
<details>
<summary>T-39 · Resetar o piloto pelos botões físicos (modo recovery).</summary>
	- [ ] Concluído
	**Como fazer:** Este passo apaga o tablet.
	**Prazo:** 
</details>
<details>
<summary>T-40 · Tentar configurar o tablet sem o Gmail.</summary>
	- [ ] Concluído
	**Como fazer:** Deve ser impossível pular a conta. Esse é o resultado esperado.
	**Prazo:** 
</details>
<details>
<summary>T-41 · Liberar com o Gmail da EFRP.</summary>
	- [ ] Concluído
	**Como fazer:** Entrar com a conta; depois remover a conta e resetar pelas Configurações.
	**Prazo:** 
</details>
<details>
<summary>T-42 · Reinstalar o piloto (Anexo C).</summary>
	- [ ] Concluído
	**Como fazer:** O piloto volta a ser o tablet CC-TAB-01.
	**Prazo:** 
</details>

<callout icon="🚦" color="blue_bg">
	**PORTÃO · saída da Fase 4**
	- [ ] TB-01 a TB-20 todos aprovados.
	- [ ] EFRP comprovada (T-40) — ou risco aceito por escrito, se o VAIO não suportar.
	- [ ] Uso real sem problemas bloqueantes; ajustes feitos e nova versão gerada, se necessário.
	- [ ] Aprovação do dono para replicar nos 11 tablets.
</callout>

# Fase 5 · Implantação dos 11 tablets
<callout icon="🎯" color="gray_bg">
	**Objetivo**
	Replicar o piloto com exatamente a mesma configuração, sem improviso.
	
	**Entrega:** 12 tablets em operação, inventário e termos assinados.
</callout>

<details>
<summary>T-43 · Comprar os 11 cases restantes e preparar as etiquetas.</summary>
	- [ ] Concluído
	**Como fazer:** CC-TAB-02 a CC-TAB-12.
	**Prazo:** 
</details>
<details>
<summary>T-44 · Instalar os tablets em lotes.</summary>
	- [ ] Concluído
	**Como fazer:** Sugestão: lotes de 3 ou 4 por dia, para não errar por cansaço.
	**Prazo:** 
</details>
<details>
<summary>T-45 · Seguir o checklist do Anexo C em cada tablet.</summary>
	- [ ] Concluído
	**Como fazer:** Uma cópia impressa do checklist por tablet, marcada e guardada.
	**Prazo:** 
</details>
<details>
<summary>T-46 · Fazer um teste rápido de burla em cada tablet.</summary>
	- [ ] Concluído
	**Como fazer:** TB-01, TB-02, TB-06, TB-07 e TB-16 (os mais rápidos).
	**Prazo:** 
</details>
<details>
<summary>T-47 · Registrar cada tablet no inventário.</summary>
	- [ ] Concluído
	**Como fazer:** Série, etiqueta, local, responsável; senha do Android no cofre.
	**Prazo:** 
</details>
<details>
<summary>T-48 · Treinar os conferentes.</summary>
	- [ ] Concluído
	**Como fazer:** 10 minutos: ligar, senha, usar o CONFER, o que fazer se cair a internet, a quem chamar.
	**Prazo:** 
</details>
<details>
<summary>T-49 · Colher a assinatura do termo de responsabilidade.</summary>
	- [ ] Concluído
	**Como fazer:** Um termo por conferente; guardar cópia.
	**Prazo:** 
</details>
<details>
<summary>T-50 · Acompanhamento da primeira semana.</summary>
	- [ ] Concluído
	**Como fazer:** Conferir todos os tablets no fim de cada dia: funcionando, carregados, sem avisos.
	**Prazo:** 
</details>

<callout icon="🚦" color="blue_bg">
	**PORTÃO · saída da Fase 5**
	- [ ] 12 tablets instalados com o checklist do Anexo C completo.
	- [ ] Inventário (Anexo D) preenchido e senhas no cofre.
	- [ ] Conferentes treinados e termos assinados.
	- [ ] Primeira semana sem incidentes bloqueantes.
</callout>

# Fase 6 · Operação e manutenção

| Frequência | Rotina | Como |
|---|---|---|
| Diária (1ª semana) | Conferir se todos os tablets estão funcionando | Ver T-50. |
| Semanal | Conferir estado físico, case, etiqueta e carregamento | Passada rápida pelos 12 tablets. |
| Mensal | Ler o registro de eventos de cada tablet | Procurar tentativas de PIN, quedas e travamentos. |
| Mensal | Verificar a versão do WebView | Se houver versão nova, atualizar por APK no modo manutenção (RF-34). |
| Mensal | Testar o acesso ao Gmail da EFRP | Entrar na conta; nunca trocar a senha sem planejar. |
| Trimestral | Trocar o PIN de administrador | Nos 12 tablets; atualizar o cofre. |
| Trimestral | Conferir os backups da chave de assinatura | Abrir o backup offline e confirmar que o arquivo está íntegro. |
| A cada mudança do CONFER | Revisar endereços e câmera | Atualizar pelo modo administrador e testar a leitura de código. |
| A cada nova versão do app | Atualizar os 12 tablets | Testar no piloto primeiro; depois replicar com o Anexo C, passo de atualização. |

# Procedimentos de contingência

| ID | Situação | O que fazer |
|---|---|---|
| C-01 | CONFER fora do ar ou com endereço novo | Se for queda: os tablets mostram a tela de sem conexão e voltam sozinhos. Se o endereço mudou: atualizar endereço e listas pelo modo administrador em cada tablet. |
| C-02 | PIN de administrador esquecido | Buscar no cofre. Se perdido de vez: reset pelos botões → liberar com o Gmail da EFRP → reinstalar (Anexo C). |
| C-03 | Tablet furtado ou sumido | Registrar no inventário; trocar o PIN dos outros tablets (pode ter sido observado); avaliar ocorrência policial. Com a EFRP, o tablet resetado não pode ser usado por terceiros. |
| C-04 | Tablet travado sem saída (erro do app) | Tentar o modo administrador. Se não abrir: tentar reiniciar. Se continuar: reset pelos botões + Gmail da EFRP + reinstalar. |
| C-05 | Tablet resetado por alguém | Liberar com o Gmail da EFRP e reinstalar (Anexo C). Registrar quem estava com o tablet. |
| C-06 | Câmera parou de ler códigos | Limpar a sessão no modo administrador; conferir a lista de endereços com câmera; testar o CONFER no piloto; verificar se o WebView precisa de atualização. |
| C-07 | Atualização do Android quebrou algo | Testar no piloto; pedir correção ao Manus; atualizar o app nos tablets afetados pelo modo manutenção. |
| C-08 | Perda da chave de assinatura | Recuperar do backup offline. Sem backup: gerar nova chave e reinstalar os 12 tablets do zero. |
| C-09 | Perda do Gmail da EFRP | Recuperar pela conta de recuperação. Sem recuperação: tablets resetados ficam inutilizáveis; aplicar um novo Gmail em todos os tablets ainda em funcionamento, o quanto antes. |

# Anexo A · Cofre de credenciais

*O que precisa estar no gerenciador de senhas compartilhado. Este documento lista apenas os nomes, nunca os valores.*

| Item | Conteúdo | Quem acessa |
|---|---|---|
| PIN de administrador | PIN atual e data da última troca | Gabriel e dono |
| Gmail da EFRP | E-mail, senha, códigos reserva da verificação em 2 etapas, ID numérico | Gabriel e dono |
| Chave de assinatura | Arquivo .jks, senha do arquivo, alias e senha da chave | Gabriel e dono |
| Senhas do Android | Uma por tablet, identificada pela etiqueta CC-TAB-xx | Gabriel e dono |
| Wi-Fi dos tablets | Nome da rede e senha | Gabriel e dono |
| Repositório | Conta, acesso e códigos reserva | Gabriel e dono |

# Anexo B · Comandos usados

| Comando | Para que serve |
|---|---|
| `adb devices` | Ver se o tablet está conectado |
| `adb install app.apk` | Instalar o app |
| `adb install -r app.apk` | Atualizar o app mantendo os dados |
| `adb shell dpm set-device-owner <pacote>/.AdminReceiver` | Tornar o app Device Owner (tablet sem contas) |
| `adb shell dpm remove-active-admin <pacote>/.AdminReceiver` | Remover o Device Owner (só na versão de teste, testOnly) |
| `adb shell pm list users` | Conferir se existe só um usuário no tablet |

*Substituir `<pacote>` pelo nome decidido na D-05. O nome exato do receptor é definido pelo Manus na etapa 1.*

# Anexo C · Checklist de instalação por tablet

Imprimir uma cópia por tablet. Etiqueta: ____________   Série: ____________   Data: ____/____/______

<details>
<summary>Passo 1 · Tablet novo ou resetado pelas Configurações. Configuração inicial sem nenhuma conta, no Wi-Fi dos tablets.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 2 · Ativar opções do desenvolvedor e depuração USB; autorizar o computador.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 3 · `adb devices` lista o tablet; `adb shell pm list users` mostra um único usuário.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 4 · Instalar o APK final da versão vigente.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 5 · Rodar o comando `set-device-owner` e confirmar a mensagem de sucesso.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 6 · Abrir o app e seguir a primeira abertura: criar o PIN, informar o ID do Gmail da EFRP.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 7 · Confirmar no modo administrador: "EFRP: ativa".</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 8 · Definir a senha do Android deste tablet e salvá-la no cofre com a etiqueta.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 9 · Testar o CONFER e a leitura de um código.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 10 · Ativar os bloqueios e entrar no quiosque (a depuração USB é desligada aqui).</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 11 · Teste rápido de burla: TB-01, TB-02, TB-06, TB-07, TB-16.</summary>
	- [ ] Concluído
</details>
<details>
<summary>Passo 12 · Colar a etiqueta, instalar o case com trava e registrar no inventário.</summary>
	- [ ] Concluído
</details>

**Atualização do app em um tablet já instalado:**

- Modo administrador → modo manutenção.
- Conectar ao computador e copiar ou instalar o novo APK (mesma chave de assinatura).
- Conferir a versão nova e a configuração preservada.
- Sair do modo manutenção e repetir o teste rápido de burla.

Responsável: ______________________________   Assinatura: ______________________________

# Anexo D · Modelo de inventário

| Etiqueta | Nº de série | Local | Responsável | Versão do app | Instalado em | Observações |
|---|---|---|---|---|---|---|
| CC-TAB-01 |  |  |  |  |  |  |
| CC-TAB-02 |  |  |  |  |  |  |
| CC-TAB-03 |  |  |  |  |  |  |
| CC-TAB-04 |  |  |  |  |  |  |
| CC-TAB-05 |  |  |  |  |  |  |
| CC-TAB-06 |  |  |  |  |  |  |
| CC-TAB-07 |  |  |  |  |  |  |
| CC-TAB-08 |  |  |  |  |  |  |
| CC-TAB-09 |  |  |  |  |  |  |
| CC-TAB-10 |  |  |  |  |  |  |
| CC-TAB-11 |  |  |  |  |  |  |
| CC-TAB-12 |  |  |  |  |  |  |

A senha do Android de cada tablet **não** vai no inventário. Ela fica no cofre (Anexo A).

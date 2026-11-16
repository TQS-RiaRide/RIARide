# 2.3 Project roadmap and priorities

> Draft for section 2.3 of the Product Specification report (RIA-9). The user stories are in the [Jira backlog](https://riaride.atlassian.net/jira/software/projects/RIA/boards/1); this section lists them by iteration.

As stories estão organizadas por iteração, da mais prioritária para a menos prioritária. O I2 tem as stories *Highest*, que formam o fluxo principal. O I3 tem as *High* e o I4 as restantes, *High* e *Medium*. O I5 não tem stories novas.

Cada story segue o formato **Como** \<papel\>, **quero** \<ação\> **para** \<benefício\> (*As a… I want… so that…*). Os critérios de aceitação seguem o formato **Dado** \<contexto\>, **quando** \<ação\>, **então** \<resultado\> (*Given… when… then…*), para depois passarem diretamente a cenários Gherkin automatizados com Cucumber.

## I2 (17–24 nov): fluxo principal

Também nesta iteração: simulador mínimo e a sua integração, CI com quality gate que bloqueia, Xray configurado, SLOs definidos e QA Manual v1. A primeira story gerada por um agente (AI4) é a 6.1.

| # | User story | Critérios de aceitação | Prioridade |
|---|---|---|---|
| 1.1 | **Registar uma estadia.** Como gestor do hotel, quero registar a estadia de um hóspede para que ele receba um código de estadia para usar o RiaRide. | Dado um hóspede a fazer check-in no Marés Praia, quando o gestor do hotel regista a estadia com as datas de check-in e check-out, então é gerado um código de estadia único. | Highest |
| 1.2 | **Registo do hóspede com código de estadia.** Como hóspede, quero criar uma conta com email, password e código de estadia para poder usar as bicicletas durante a estadia. | • Dado um código válido de uma estadia ativa, quando o hóspede se regista, então a conta é criada.<br>• Dado um código de uma estadia que já terminou, quando o hóspede se tenta registar, então o registo é recusado com uma mensagem clara. | Highest |
| 2.1 | **Comprar um passe.** Como hóspede, quero comprar um passe de 1 dia, de 3 dias ou de estadia para poder andar de bicicleta durante a estadia. | Dado um hóspede autenticado com uma estadia ativa, quando compra um passe de estadia, então o preço e a caução são cobrados na sandbox de pagamentos e o passe fica criado, mas ainda não ativo. | Highest |
| 3.1 | **Ver bicicletas disponíveis.** Como hóspede, quero ver as bicicletas disponíveis por hub e tipo para saber onde levantar uma. | Dado um hub com 3 bicicletas disponíveis e 1 com 15 % de bateria, quando o hóspede consulta esse hub, então só as 3 disponíveis aparecem como “podem ser reservadas”. | Highest |
| 3.2 | **Começar uma viagem já.** Como hóspede, quero começar uma viagem com uma bicicleta disponível para poder sair de imediato. | • Dado um hóspede com um passe válido e sem viagem em curso, quando começa uma viagem, então a bicicleta é reservada, a viagem fica a iniciar e é enviado o comando de desbloqueio.<br>• Dado uma viagem a iniciar, quando a telemetria confirma o desbloqueio, então a viagem começa e a bicicleta passa a em uso.<br>• Dado uma viagem a iniciar, quando o desbloqueio não é confirmado dentro de 30 segundos, então a viagem falha, o passe continua inativo e a bicicleta fica reservada até a reserva expirar.<br>• Dado um hóspede com uma viagem em curso, quando tenta começar outra, então o pedido é recusado. | Highest |
| 3.4 | **Terminar a viagem num hub.** Como hóspede, quero terminar a viagem em qualquer hub com lugar livre para não ter de voltar ao ponto de partida. | Dado um hub com a capacidade cheia, quando o hóspede tenta terminar a viagem nesse hub, então o pedido é recusado e são sugeridos os hubs mais próximos com lugares livres. | Highest |
| 6.1 | **Registar um hub.** Como operador, quero registar hubs com a sua capacidade para saber onde ficam as bicicletas. | Dado um hub novo no Marés Praia com capacidade para 10 bicicletas, quando o operador o regista, então o hub aparece na frota com 10 lugares. | Highest |
| 6.2 | **Registar uma bicicleta.** Como operador, quero registar bicicletas num hub para que fiquem disponíveis para os hóspedes. | Dado um hub com lugares livres, quando o operador regista uma bicicleta nesse hub, então a bicicleta fica disponível. | Highest |
| 9.1 | **Receber telemetria do simulador.** Como operador, quero que o sistema receba a bateria, a posição e o estado de cada bicicleta para ter a frota atualizada. | Dado o simulador a enviar dados via MQTT, quando chega uma leitura de uma bicicleta, então o estado dessa bicicleta é atualizado em menos de 5 segundos. | Highest |
| 9.2 | **Bloquear bicicletas com pouca bateria.** Como hóspede, quero que só me deixem reservar bicicletas com bateria suficiente para não ficar parado a meio do caminho. | Dado uma bicicleta disponível, quando a telemetria indica 18 % de bateria, então a bicicleta deixa de poder ser reservada. | Highest |

## I3 (24 nov–1 dez): regras de prazos, manutenção e API completa

Também nesta iteração: deploy automático (CD), persistência e testes de integração. No fim, a API REST tem de estar completa. A segunda story gerada por um agente (AI4) é a 7.2.

| # | User story | Critérios de aceitação | Prioridade |
|---|---|---|---|
| 1.3 | **Login e papéis.** Como utilizador, quero fazer login e ver só as funcionalidades do meu papel para aceder apenas ao que preciso. | Dado um utilizador com o papel de operador, quando faz login, então vê o dashboard do operador e não o backoffice do hotel. | High |
| 2.2 | **Ativação do passe na primeira viagem.** Como hóspede, quero que o passe só comece na primeira viagem para não perder tempo que ainda não usei. | Dado um hóspede com um passe de 1 dia nunca usado, quando o desbloqueio da primeira viagem é confirmado, então o passe fica ativo e expira 24 horas depois. | High |
| 2.3 | **Cobrança de excesso.** Como gestora do hotel, quero que as viagens longas sejam cobradas como excesso para que as bicicletas voltem aos hubs. | Dado uma viagem que durou 3 h 20 min, quando termina, então o hóspede paga 2 blocos de 15 minutos de excesso. | High |
| 2.5 | **Fim do passe durante uma viagem.** Como gestora do hotel, quero que o tempo de viagem depois do fim do passe seja cobrado como excesso para que os hóspedes não usem as bicicletas sem passe. | • Dado um passe de 1 dia que expira às 15h00 e uma viagem em curso, quando a viagem termina às 15h30, então o hóspede paga 2 blocos de 15 minutos de excesso.<br>• Dado um passe de estadia com check-out às 12h00 e uma bicicleta em uso, quando a viagem termina às 12h20, então o hóspede paga 2 blocos de 15 minutos de excesso. | High |
| 3.3 | **Reservar uma bicicleta com antecedência.** Como hóspede, quero reservar uma bicicleta com antecedência para que esteja à minha espera quando chegar. | • Dado uma bicicleta disponível, quando o hóspede a reserva, então a bicicleta passa a reservada.<br>• Dado uma bicicleta reservada, quando passam 15 minutos sem a viagem começar, então a reserva é cancelada e a bicicleta volta a disponível. | High |
| 6.3 | **Mudar o estado de uma bicicleta.** Como operador, quero ativar, desativar ou retirar bicicletas para controlar quais podem ser usadas. | • Dado uma bicicleta disponível, quando o operador a retira, então passa a retirada e deixa de aparecer aos hóspedes.<br>• Dado uma bicicleta em uso, quando o operador a tenta retirar, então o pedido é recusado. | High |
| 7.1 | **Abrir um ticket de manutenção.** Como operador, quero abrir um ticket para uma bicicleta avariada para que seja reparada dentro do prazo. | Dado uma bicicleta disponível com uma avaria, quando o operador abre um ticket, então a bicicleta passa a manutenção e o ticket recebe um prazo SLA. | High |
| 7.2 | **Atribuir um ticket a um técnico.** Como operador, quero atribuir tickets a técnicos para dividir o trabalho. | Dado um ticket aberto, quando o operador o atribui a um técnico, então o ticket aparece na lista desse técnico. | High |
| 7.3 | **Fechar um ticket.** Como técnico, quero fechar um ticket depois da reparação para que a bicicleta volte ao serviço. | Dado uma bicicleta em manutenção com o ticket atribuído ao técnico, quando o técnico fecha o ticket, então a bicicleta volta a disponível. | High |
| 7.4 | **Alerta de prazo ultrapassado.** Como operador, quero ser avisado quando um ticket passa do prazo para cumprir o SLA. | Dado um ticket aberto, quando o prazo SLA passa sem o ticket estar fechado, então o ticket fica marcado como fora do SLA no dashboard. | High |
| 8.2 | **Definir as regras de uso.** Como gestora do hotel, quero definir a duração máxima das viagens e a capacidade dos hubs para controlar o serviço. | • Dado a duração máxima alterada para 2 horas, quando uma viagem passa das 2 horas, então começa a ser cobrado excesso.<br>• Dado um hub com 8 bicicletas cuja capacidade passa para 8, quando um hóspede tenta terminar ali uma viagem, então o pedido é recusado. | High |
| 9.3 | **Ticket automático por avaria comunicada pela telemetria.** Como operador, quero que uma avaria comunicada por uma bicicleta abra logo um ticket de manutenção para que a reparação comece sem esperar que um hóspede se queixe. | • Dado uma bicicleta disponível, quando o simulador comunica uma avaria nos travões, então é aberto um ticket com prazo SLA e a bicicleta passa a manutenção.<br>• Dado uma bicicleta em uso, quando o simulador comunica uma avaria, então é aberto logo um ticket, a viagem continua e a bicicleta só passa a manutenção quando a viagem termina.<br>• Dado uma avaria já comunicada com um ticket aberto, quando chega de novo a mesma mensagem (mesmo `messageId`), então não é aberto um segundo ticket. | High |

## I4 (1–8 dez): MVP completo e testes de sistema

Foco desta iteração: dados representativos, testes end-to-end (Playwright ou Selenium), teste de carga k6 contra os SLOs e práticas valorizadas. As stories são sobretudo ecrãs e dashboards, que leem dados que já existem, e são o que os testes end-to-end vão verificar.

| # | User story | Critérios de aceitação | Prioridade |
|---|---|---|---|
| 1.4 | **Gerir contas e papéis.** Como administrador, quero gerir as contas e os papéis dos utilizadores para controlar quem acede a quê. | • Dado um utilizador sem papel de técnico, quando o administrador lhe atribui esse papel, então o utilizador passa a ver a lista de tickets.<br>• Dado uma conta desativada pelo administrador, quando o utilizador tenta fazer login, então o login é recusado. | High |
| 2.4 | **Devolução da caução.** Como hóspede, quero receber a caução de volta no fim do passe para pagar apenas o que usei. | Dado um passe que terminou sem viagens em curso nem avarias reportadas, quando o passe termina, então a caução é devolvida na sandbox de pagamentos. | Medium |
| 4.1 | **Criar uma rota sugerida.** Como gestora do hotel, quero criar rotas com uma lista ordenada de hubs para sugerir passeios aos hóspedes. | • Dado uma rota que começa e acaba num hotel, quando a gestora a guarda, então a rota fica publicada.<br>• Dado uma rota que não começa nem acaba num hotel, quando a gestora a tenta guardar, então o pedido é recusado. | Medium |
| 4.2 | **Consultar uma rota.** Como hóspede, quero ver as rotas sugeridas e as bicicletas disponíveis no primeiro hub para planear o meu passeio. | Dado uma rota publicada, quando o hóspede a abre, então vê os hubs por ordem e o número de bicicletas disponíveis no primeiro hub. | Medium |
| 5.1 | **Ver o meu passe.** Como hóspede, quero ver o tipo e a validade do meu passe para saber até quando posso usar as bicicletas. | Dado um passe de estadia ativo, quando o hóspede abre o dashboard, então vê que o passe termina no dia e hora do check-out. | High |
| 5.2 | **Ver viagens e custos.** Como hóspede, quero ver as minhas viagens e o que paguei para não ter surpresas. | Dado um hóspede com duas viagens, uma delas com excesso, quando abre o histórico, então vê as duas viagens e o valor do excesso. | High |
| 5.3 | **Mudar a língua.** Como hóspede estrangeiro, quero usar o RiaRide em inglês para perceber todas as informações. | Dado um hóspede na página inicial, quando escolhe inglês, então todos os textos aparecem em inglês. | Medium |
| 6.4 | **Dashboard da frota.** Como operador, quero ver o estado da frota e os tickets abertos para saber onde faltam bicicletas e o que está por reparar. | Dado um hub cheio, outro quase vazio e dois tickets abertos, quando o operador abre o dashboard, então vê a ocupação de cada hub e os dois tickets. | High |
| 8.1 | **Definir o preço base dos passes.** Como gestora do hotel, quero definir o preço base de cada tipo de passe para que seja igual nos três hotéis. | • Dado um novo preço para o passe de 1 dia, quando a gestora o guarda, então as novas compras nos três hotéis usam esse preço.<br>• Dado um passe já comprado, quando o preço muda, então o passe mantém o preço antigo. | High |
| 8.3 | **Ver os KPIs do serviço.** Como gestora do hotel, quero ver a utilização, a disponibilidade face ao SLA, as viagens por dia e o tempo de reparação para avaliar o operador. | Dado os dados do último mês, quando a gestora abre os KPIs, então vê os quatro indicadores, por hotel. | High |
| 8.4 | **Ver as métricas técnicas.** Como gestora do hotel, quero ver a latência da API, a taxa de erros e o atraso da telemetria para saber se a plataforma está a funcionar bem. | Dado a plataforma em funcionamento, quando a gestora abre as métricas técnicas, então vê os três valores atualizados. | High |

## I5 (8–15 dez): estabilizar e entregar

Sem stories novas. O MVP já tem de estar completo e em produção no início desta iteração. O trabalho é:

- corrigir erros;
- terminar o Product Specs, o QA Manual e a análise do uso de IA (AI5);
- preparar a apresentação e a demo;
- criar a tag `final_v1` até ao dia da apresentação.

Se alguma story do I4 se atrasar, acaba-se aqui, mas não entram stories novas.

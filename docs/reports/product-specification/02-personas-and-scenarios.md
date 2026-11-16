# 2.2 Personas and scenarios

> Draft for section 2.2 of the Product Specification report (RIA-8). There is one persona for each main actor (guest, operator, hotel manager) and one scenario for each persona.

O técnico e o administrador não têm persona própria. O técnico aparece no cenário do Rui, e o administrador só gere contas e papéis.

## Sophie Laurent, a hóspede

- **Idade e profissão:** 34 anos, designer, de Lyon (França).
- **Contexto:** está 4 noites no hotel Marés Praia com o namorado. Fala francês e inglês, mas não fala português.
- **Objetivos:** conhecer Vila Ria de bicicleta ao seu ritmo; saber sempre quanto vai pagar.
- **Frustrações:** apps que pedem documentos ou números de telefone locais; custos escondidos; bicicletas sem bateria.

## Rui Matos, o operador da frota

- **Idade e profissão:** 41 anos, responsável de operações na RodaLeve Fleet Services.
- **Contexto:** gere as bicicletas de todos os hubs e coordena dois técnicos.
- **Objetivos:** ter bicicletas a funcionar em todos os hubs; reparar avarias dentro do prazo do SLA; mudar bicicletas de hub antes das horas de mais procura.
- **Frustrações:** só saber de uma avaria quando um hóspede se queixa; hubs cheios num hotel e vazios noutro.

## Ana Ribeiro, a gestora do hotel

- **Idade e profissão:** 45 anos, responsável pela experiência dos hóspedes no Grupo Hoteleiro Marés.
- **Contexto:** gere o serviço de bicicletas nos três hotéis do grupo.
- **Objetivos:** oferecer as bicicletas como um serviço que os hóspedes valorizam; definir o preço base dos passes, igual para os três hotéis do grupo; confirmar que o operador cumpre o SLA, o acordo de nível de serviço assinado com o grupo.
- **Frustrações:** pagar por um serviço sem saber quanto é usado nem quantas vezes as bicicletas estão indisponíveis.

## Cenários

### Cenário 1: Sophie passeia até às salinas

Sophie faz check-in no Marés Praia e recebe um código de estadia na receção. No telemóvel, muda o RiaRide para inglês e cria uma conta com email, password e o código de estadia. Escolhe o passe de estadia em vez do passe de 1 dia ou de 3 dias, e a caução é cobrada no cartão.

Na manhã seguinte, abre a rota sugerida “Marés Praia → Centro histórico → Salinas → Marés Praia” e vê que há três bicicletas no hub do hotel. Quando começa a primeira viagem, o passe fica ativo. Vai até ao centro histórico e entrega a bicicleta num hub com lugar livre. Depois do almoço, levanta outra bicicleta e segue até às salinas.

No último dia, entrega a bicicleta antes do check-out. O passe termina no check-out e a caução é devolvida. No dashboard, Sophie vê todas as viagens e o total pago, sem custos extra.

### Cenário 2: Rui resolve uma avaria antes da hora de ponta

Às 8h, Rui abre o dashboard do operador. Vê que uma bicicleta no hub do Centro histórico comunicou uma avaria nos travões durante a noite. A partir da posição das bicicletas, o dashboard mostra também que o hub do Marés Centro está cheio e o das Salinas quase vazio.

Durante a noite, a avaria abriu automaticamente um ticket de manutenção com prazo SLA, e a bicicleta passou a manutenção, para que nenhum hóspede a possa reservar. Rui atribui o ticket a um técnico. Depois, combina com a equipa levar quatro bicicletas do Marés Centro para as Salinas numa carrinha. Esta tarefa é feita fora do RiaRide.

À tarde, o técnico repara os travões e fecha o ticket. A bicicleta volta a ficar disponível.

### Cenário 3: Ana prepara a época de verão

Antes do verão, Ana entra no backoffice do hotel. Atualiza o preço base do passe de 1 dia, do passe de 3 dias e do passe de estadia, que se aplica aos três hotéis do grupo. Aumenta também a capacidade do hub do Marés Praia, que no ano anterior estava muitas vezes cheio.

Depois, consulta os indicadores do último mês: utilização das bicicletas, disponibilidade face ao SLA, viagens por dia e tempo médio de reparação. Repara que a disponibilidade no Marés Salinas ficou abaixo do SLA em duas semanas e marca uma reunião com o operador para falarem sobre isso.

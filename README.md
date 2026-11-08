# Caderno do Pescador

Aplicação Android nativa (Kotlin) para pescadores em Portugal, desenvolvida para a unidade curricular de PDM (UBI).
Funciona **sem Internet**.

Tomás Leal 53733

## Descrição

O pescador consulta locais de pesca (lista e mapa de Portugal), regista as suas **licenças** e as suas **capturas**,
vê se pode pescar em cada local (verde / laranja / vermelho consoante a licença), coleciona espécies na **Peixedex**
(estilo Pokédex) e acompanha **estatísticas** e **conquistas**.

## Funcionalidades

- **Pesqueiros**: 22 locais, com pesquisa, filtros, ordenação, lista paginada e mapa de Portugal desenhado com `Canvas`.
  É possível criar, editar e eliminar locais próprios.
- **Licenças**: adicionar, editar e eliminar (com validação de datas e duração máxima de 1 ano); aviso de expiração
  dentro da app e por notificação local.
- **Capturas**: criar, ver (lista e detalhe), editar e eliminar, com espécie, local, peso, data, isco e fotografia.
  Só se aceita uma espécie que exista no local escolhido.
- **Peixedex**: 20 espécies, desbloqueadas ao registar uma captura; guia de espécies.
- **Estatísticas**: totais, recorde, local mais visitado, gráfico por mês, espécies mais capturadas.
- **Conquistas**: 10 conquistas calculadas a partir das capturas, com ecrã próprio.

### Funcionalidades adicionais

- Pesquisa no histórico de capturas (espécie, local ou data).
- Três idiomas (português, inglês, italiano), escolhidos em Definições → Idioma.
- Cópia de segurança: exportar e importar licenças e capturas em JSON (Estatísticas).
- Modo claro e escuro, partilha de capturas (WhatsApp / SMS), notificações de licenças a expirar, foto de perfil.
- Layout adaptado a ecrãs maiores (margens e colunas por largura).
- Testes unitários (20).

## Como abrir, compilar e testar

**Requisitos:** Android Studio recente (o projeto usa o Android Gradle Plugin 9.3), JDK incluído no Android Studio,
`minSdk` 29 (Android 10).

1. Android Studio → **Open** → escolher a pasta do projeto e esperar pela sincronização do Gradle.
2. Escolher um dispositivo (emulador API 29 ou superior, ou telemóvel) e carregar em **Run**.
3. Linha de comandos: `./gradlew assembleDebug` (o APK fica em `app/build/outputs/apk/debug/`).
4. Testes unitários: `./gradlew testDebugUnitTest` (ou clicar com o botão direito em `app/src/test` → Run Tests).

**Para experimentar:** a app começa sem licenças nem capturas. Em **Definições → Carregar dados de exemplo** aparecem
dados de demonstração. Sugestão de teste: registar uma captura (separador Capturas), ver a espécie desbloqueada na
Peixedex, editar e eliminar a captura, exportar a cópia de segurança nas Estatísticas, usar «Começar do zero» e importar
o ficheiro, e mudar o idioma em Definições.

## Tecnologia de armazenamento

**SQLite** (`SQLiteOpenHelper`, ficheiro `caderno_pescador.db`, versão 3, chaves estrangeiras ativas). Os registos do
utilizador ficam em SQLite; `SharedPreferences` guarda só definições (tema, dias de aviso, idioma, nome e foto do perfil).
As fotografias das capturas ficam em ficheiros na pasta interna da app e a base guarda só o caminho.

## Estrutura dos dados

Uma tabela por assunto; o texto que muda com a língua fica numa tabela `*_texto` (uma linha por idioma, com o
português como alternativa quando falta uma tradução).

- **Dados de referência** (vêm com a app): `idiomas`, `distritos`, `tipos_licenca` + `tipo_licenca_texto`,
  `iscos` + `isco_texto`, `especies` + `especie_texto`, `especie_isco`, `especie_sinal` + `especie_sinal_texto`,
  `pesqueiros` + `pesqueiro_texto`, `pesqueiro_especie`, `pesqueiro_licenca`.
- **Dados do utilizador**: `licencas` (id, tipo, número, início, fim), `capturas` (id, data, pesqueiro, espécie, peso,
  isco, foto) e `desbloqueadas` (espécie).
- As relações usam chaves estrangeiras; as relações muitos-para-muitos usam tabelas de ligação.

## Organização do código (`app/src/main/java/pt/ubi/di/pdm/aplicacaopesca/`)

- `data/`: `Repositorio` (único ponto de acesso à base; a interface nunca escreve SQL), `BaseDados` (esquema e migrações),
  `DadosIniciais*` (dados que preenchem a base na primeira execução), `Catalogo` (cache em memória), `Modelos`,
  `Conquistas`, `CopiaSeguranca`.
- `ui/`: Activities, Fragments, diálogos e `Views` próprias (mapa, gráfico, ilustração de espécies).
- `util/`: funções de apoio (datas, cores, dimensões).

## Decisões de implementação

- **Views XML + Material 3**, sem Compose, e **SQLite nativo** em vez de Room (evita bibliotecas extra).
- **Repositório único** para separar interface e dados; as regras puras (por exemplo `estadoDe`) ficam em `Modelos.kt`.
- **Mapa desenhado à mão** com `Canvas`, para funcionar offline e sem bibliotecas de mapas.
- **Língua** com a API do Android (`AppCompatDelegate.setApplicationLocales`); as traduções vão dentro da app, em vez
  de uma API de tradução online, para não depender da Internet.
- **Conquistas calculadas**, não guardadas: ficam sempre certas ao editar, apagar ou importar capturas.

## Dificuldades

- Gerir as fotografias das capturas: guardá-las em ficheiros na pasta interna da app e, ao editar uma captura, só
  apagar a foto antiga depois de a alteração ficar guardada (para não a perder se o utilizador cancelar).
- Desenhar o mapa de Portugal e os gestos (arrastar, toque) sem biblioteca externa.
- Ter os textos e os dados de referência em três idiomas e as notificações no idioma escolhido.
- Adaptar os ecrãs a diferentes tamanhos (telemóvel e tablet).

## Bibliotecas utilizadas

Apenas as do Android: AndroidX (`core-ktx`, `appcompat`, `activity-ktx`, `constraintlayout`) e Material Components.
Testes: JUnit 4. A cópia de segurança usa `org.json`, que já faz parte do Android. A app não pede acesso à Internet.

## Utilização de inteligência artificial e fontes

- Foi utilizado o **Claude Code (Anthropic)** como ferramenta de apoio ao desenvolvimento: escrita (de algumas partes do codigo) e revisão do mesmo,
  organização do projeto e traduções para inglês e italiano (não revistas por um falante nativo).
  A escolha do tema, das funcionalidades e do aspeto da aplicação, os testes no emulador e a validação do resultado
  foram feitos pelo estudante, que é responsável pelo projeto.
- **Fotografias das espécies** (`app/src/main/res/drawable-nodpi/foto_*.jpg`): obtidas na Wikipédia / Wikimedia Commons
  (licenças livres de cada autor) e incluídas na app.
- **Coordenadas** das barragens: Wikipédia e Structurae (aproximadas).
- Os ícones são desenhos vetoriais feitos para o projeto. Não há dados pessoais, credenciais ou chaves no projeto.

# Music Wallpaper para o Paperize — como aplicar e compilar

Isto adiciona ao Paperize um ecrã novo (Definições → "Music Wallpaper") onde ativas
a deteção de música e escolhes exatamente que apps têm permissão para mudar o teu
wallpaper com a capa do álbum a tocar.

Ficheiros incluídos: 5 novos + 4 modificados (Routes, NavigationGraph, SettingsScreen,
AndroidManifest) + 1 workflow do GitHub Actions que compila o APK automaticamente.

## Passo 1 — Fazer fork do Paperize
1. Abre https://github.com/Anthonyy232/Paperize
2. Clica **Fork** (canto superior direito) → cria uma cópia na tua conta.

## Passo 2 — Enviar estes ficheiros para o teu fork
No teu fork (não no original):
1. Clica **Add file → Upload files**.
2. Arrasta as pastas `app` e `.github` desta pasta (não os ficheiros soltos — as
   pastas inteiras, para o GitHub preservar os caminhos exatos).
3. Como os caminhos já existem no repositório (ex: `app/src/main/AndroidManifest.xml`),
   o GitHub vai **substituir** esses 4 ficheiros e **criar** os 5 novos automaticamente.
4. Em baixo, escreve uma mensagem tipo "Add music wallpaper feature" e clica
   **Commit changes directly to the master branch**.

## Passo 3 — Ativar o GitHub Actions (só na primeira vez)
1. Vai ao separador **Actions** do teu fork.
2. Se aparecer um aviso a dizer que os workflows estão desativados, clica
   **"I understand my workflows, go ahead and enable them"**.
3. O build deve começar sozinho por causa do commit do Passo 2. Se não começar,
   clica em **Build Debug APK** na lista à esquerda → **Run workflow**.

## Passo 4 — Descarregar o APK
1. Espera o run ficar verde (✓), demora uns 3-6 minutos.
2. Clica no run terminado → em baixo, em **Artifacts**, descarrega
   `paperize-debug-apk` (é um .zip).
3. Descomprime — lá dentro está o `app-debug.apk`.
4. Transfere esse ficheiro para o telemóvel e abre-o para instalar
   (o Android vai pedir para autorizares "instalar apps de fontes desconhecidas"
   na app que usaste para abrir o ficheiro — normal para APKs fora da Play Store).

## Passo 5 — Configurar no telemóvel
1. Abre a app → Definições → **Music Wallpaper**.
2. Ativa o switch principal.
3. Toca em **"Grant notification access"** → ativa o Paperize na lista do Android.
4. Na lista de apps, ativa só as que queres que mudem o wallpaper (ex: Spotify),
   deixando as outras (ex: YouTube) desligadas.

## Notas importantes
- É um **build debug**, assinado com uma chave de teste do Android — normal e
  funcional para uso pessoal, mas o Android vai sempre mostrar o aviso de
  "fonte desconhecida" ao instalar.
- Se o build falhar (✗ vermelho no Actions), abre o log do passo "Build debug APK"
  e copia-me o erro — não consegui compilar isto localmente antes de te entregar,
  por isso pode precisar de um ajuste pontual.
- Isto é código novo, isolado do resto do Paperize (preferências próprias, ecrã
  próprio) — não deve afetar as funcionalidades existentes da app.

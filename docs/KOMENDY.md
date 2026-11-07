# Komendy i uprawnienia

Zestawienie z `plugin.yml` oraz z kodu komend. Komendy graczy nie wymagają uprawnień, a komendy administracyjne sprawdzają uprawnienie wymienione w tabeli (część kodu sprawdza `zamrozenie.use`, którego nie ma w `plugin.yml`, więc domyślnie działa tylko dla OP).

## Dla graczy

| Komenda | Aliasy | Co robi |
|---|---|---|
| `/tutorial` | `/poradnik` | poradnik serwera w GUI |
| `/drop` | | pule dropu bossów i zrzutu |
| `/zrzut` | | godzina i status zrzutu |
| `/shop` | `/sklep` | sklep: skup i sprzedaż serwerowa |
| `/rynek` | | oferty graczy |
| `/wystaw` | | wystawia przedmiot z ręki na rynek |
| `/topki` | `/topka`, `/topsy` | topki siana, zabójstw i śmierci |
| `/home` | | 2 sloty domów: `/home`, `/home set <1\|2>`, GUI |
| `/tpa` `/tpaccept` `/tpadeny` | | prośby o teleport (wygasają po 60 s) |
| `/klan` | | system klanów (tabela niżej) |
| `/lifesteal` | | twoje serca |
| `/wyplacserce` | | zamienia serce na przedmiot (potwierdzenie w 30 s) |
| `/smok` | | GUI z informacjami o Smoku Kresu |
| `/wysrodkuj` | | szuka bezpiecznego środka kręgu dla N graczy |

### `/klan`

| Podkomenda | Co robi |
|---|---|
| `utworz`, `rozwiaz` | tworzenie i rozwiązanie klanu |
| `zapros`, `akceptuj`, `odrzuc` | zaproszenia (max 4 osoby w klanie) |
| `wyrzuc`, `opusc` | wyrzucenie członka i wyjście z klanu |
| `awansuj`, `zdegraduj` | zmiana rangi (LIDER, ADMIN, CZŁONEK) |
| `info`, `lista` | informacje o klanie i lista klanów |
| `sethome`, `home` | dom klanu |
| `chat` | czat klanowy |
| `kolor` | kolor tagu klanu (czat, tab, nick) |
| `sojusz` | sojusze między klanami |

## Dla administratorów

| Komenda | Uprawnienie | Co robi |
|---|---|---|
| `/zamrozenie <czas>` / `stop` | `zamrozenie.use` | zamraża moby i graczy do 24 h (`60s`, `5m`, `1h30m`) |
| `/wardensword` | `zamrozenie.wardensword` | daje Warden Sword |
| `/kataklizm` (`/voidbow`, `/kataklizmpustki`) | `zamrozenie.kataklizm` | daje Kataklizm Pustki |
| `/kilof3x3` (`/netherpick`, `/kilofotchlani`) | `zamrozenie.kilof3x3` | daje Kilof Otchłani |
| `/odlamekkilofa` (`/odlamek`, `/pickshard`) | `zamrozenie.kilof3x3` | daje Odłamek Kilofa |
| `/dragongive` | `zamrozenie.use` | daje przedmiot Smoka Kresu |
| `/aktywujsmoka` | tylko OP | uruchamia event Smoka Kresu |
| `/adminzrzut <HH:mm \| off>` | `zamrozenie.use` | ustawia godzinę codziennego zrzutu |
| `/adminzrzuttest`, `/adminzrzutotworz` | `zamrozenie.use` | test i otwarcie zrzutu |
| `/asklep` | `zamrozenie.asklep` | edycja cen sklepu |
| `/adrop` | `zamrozenie.adrop` | edycja dropu bossa |
| `/zrespbossa` (`/zrespboss`) | `zamrozenie.zrespbossa` | respi bossa MythicMobs |
| `/ac` (`/anticheat`, `/zac`) | `zamrozenie.ac` | alerty i VL anticheata |
| `/lifesteal info\|set\|add\|take\|reset\|give` | `zamrozenie.use` | zarządzanie sercami |

## Uprawnienia

| Uprawnienie | Domyślnie | Opis |
|---|---|---|
| `zamrozenie.wardensword` | OP | daje Warden Sword |
| `zamrozenie.kataklizm` | OP | daje Kataklizm Pustki |
| `zamrozenie.kilof3x3` | OP | daje Kilof Otchłani i Odłamek |
| `zamrozenie.adrop` | OP | edycja dropu bossów |
| `zamrozenie.asklep` | OP | edycja sklepu |
| `zamrozenie.ac` | OP | dostęp do `/ac` |
| `zamrozenie.ac.alerts` | OP | dostaje alerty anticheata |
| `zamrozenie.ac.bypass` | nikt | omija wszystkie checki anticheata |
| `zamrozenie.zrespbossa` | OP | respienie bossa |
| `zamrozenie.nether` | OP | wejście do Netheru, gdy jest zamknięty dla graczy |
| `zamrozenie.seeplugins` | OP | `/plugins`, `/pl`, `/ver` |
| `zamrozenie.use` | OP (niezadeklarowane) | sprawdzane w kodzie przy `/zamrozenie`, `/lifesteal` (admin), `/dragongive`, `/adminzrzut*` |

Jest też jedna komenda-żart, `/kepicgowno`, która wysyła losową linijkę tekstu.

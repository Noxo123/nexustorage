# NexusStorage

Plugin Minecraft Paper (1.21) indépendant, système de stockage interconnecté
type "réseau" (Applied Energistics-like) mais vanilla-friendly.

## Fonctionnalités

### Stockage interconnecté
- **Nexus Core** (`/nexus give core`) : un Lodestone spécial. Le poser crée
  ton réseau Nexus (ou en ajoute un point d'accès si tu en as déjà un).
- **Nexus Tablet** (`/nexus give tablet`) : clic droit pour ouvrir le GUI
  de ton réseau.
- **Stockage virtuel paginé** : jusqu'à 1000 pages de 45 slots selon ton
  tier, sauvegardé en YAML (`storage/<uuid>/page_N.yml`).
- **Gestion des accès** : owner + membres avec 4 niveaux de permission
  (lecture seule, dépôt, retrait, admin).
- **Upgrades Vault** : 5 tiers (3 → 1000 pages), coûts configurables dans
  `config.yml`.

### Système d'énergie Nexus (réseau physique de câbles, style RF/FE)
10 blocs complexes, à obtenir via `/nexus give <type>` :

| Type (`/nexus give ...`) | Bloc en jeu | Rôle |
|---|---|---|
| `energycore` | Beacon | Ancre un réseau physique de câbles à TON réseau Nexus (obligatoire) |
| `solarpanel` | Sea Lantern | Génère de l'énergie le jour, si le ciel est visible |
| `solarpanel2` | End Rod | Panneau avancé : plus de production, nécessite Y ≥ 100 |
| `capacitor` | Copper Block | Stocke l'énergie (capacité de base) |
| `capacitor2` | Netherite Block | Stocke beaucoup plus d'énergie |
| `cable` | Iron Bars | Transporte l'énergie, légère perte par bloc |
| `cable2` | Chain | Câble isolé, perte quasi nulle |
| `interface` | Hopper | Consomme de l'énergie pour transférer des items entre un coffre adjacent et ton stockage Nexus virtuel |
| `regulator` | Observer | Coupe les Interfaces si l'énergie stockée passe sous un seuil (clique pour ajuster, shift-clique pour baisser) |
| `monitor` | Lecteur (Lectern) | Clic droit → tableau de bord temps réel du réseau physique local |

La simulation tourne toutes les `energy.tick-interval` ticks (20 par défaut =
1x/seconde) : elle reconstruit les réseaux connectés (BFS depuis chaque
Energy Core), calcule la production solaire, applique la perte des câbles,
remplit les capacitors, fait fonctionner les Interfaces et vérifie les
Regulators.

### GUI complet
- **📦 Storage** — pages de stockage virtuel.
- **👥 Access** — gestion des membres et permissions.
- **💰 Upgrades** — achat des tiers.
- **⚙ Settings** — infos réseau, stats d'énergie en direct, renommage du
  réseau (saisie chat), notifications on/off, raccourci vers Access.
- **⚡ Energy** — vue agrégée de tous tes réseaux physiques d'énergie
  (capacité, stockage, production/consommation par cycle, machines).
- **📱 Tablet Link** — infos rapides (owner, nombre de cores).

## Structure

```
src/main/java/com/novusmc/nexusstorage/
  Main.java
  managers/   NexusManager, NexusAccessManager, NexusStorageManager,
              NexusUpgradeManager, EconomyManager, EnergyManager
  listeners/  NexusCoreListener, NexusTabletListener, NexusGUIListener,
              EnergyListener
  gui/        NexusGUIManager + holders/ (Main, Storage, Access, Upgrade,
              Settings, Energy)
  commands/   NexusCommand
  model/      NexusNetwork, AccessLevel, EnergyBlockType, EnergyGraph
```

## Compiler le plugin

Ce sandbox n'a pas accès aux dépôts Maven de PaperMC / JitPack, je n'ai donc
**pas pu compiler le `.jar` ici**. Deux options pour l'obtenir :

### Option A — GitHub Actions (recommandé)
1. Pousse ce dossier dans un repo GitHub.
2. Le workflow `.github/workflows/build.yml` est déjà inclus : il compile
   automatiquement à chaque push et dépose `NexusStorage.jar` dans les
   artefacts de l'action (onglet **Actions** → run → **Artifacts**).

### Option B — Compilation locale
Avec Maven et une connexion internet :
```bash
mvn clean package
```
Le fichier `target/NexusStorage.jar` est généré (shade plugin inclus,
pas besoin d'installer Vault à part, `paper-api` reste `provided`).

## Config

Voir `src/main/resources/config.yml` pour les coûts Vault et les paliers
d'upgrade (nombre de pages par tier).

## Limites connues (système d'énergie)

- La perte des câbles est calculée par une approximation globale sur le
  nombre de câbles du réseau (plafonnée à 60%), pas un calcul exact par
  chemin le plus court — suffisant pour du gameplay mais pas une simulation
  physique parfaite.
- Un bloc `interface` ne bouge qu'un seul item par cycle et prend le premier
  coffre/conteneur adjacent trouvé.
- Si deux `energycore` finissent connectés au même réseau physique, seul le
  premier trouvé lors du parcours (BFS) récupère les blocs partagés.

## Notes techniques

- L'ajout de membre se fait via une saisie chat simple (tape le pseudo)
  plutôt qu'une anvil GUI, pour rester robuste sans dépendance NMS.
- Chaque page de stockage utilise une inventaire 54 slots : 45 slots de
  stockage réel (0-44) + une rangée de navigation (45 = page précédente,
  49 = indicateur, 53 = page suivante).
- Vault est en **softdepend** : si absent, tous les coûts sont ignorés
  automatiquement (aucune erreur).

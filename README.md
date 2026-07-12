# NexusStorage

Plugin Minecraft Paper (1.21) indépendant, système de stockage interconnecté
type "réseau" (Applied Energistics-like) mais vanilla-friendly.

## Fonctionnalités

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
- **GUI premium** avec Amethyst Shard, Compass, Book, Clock, Nether Star.

## Structure

```
src/main/java/com/novusmc/nexusstorage/
  Main.java
  managers/   NexusManager, NexusAccessManager, NexusStorageManager,
              NexusUpgradeManager, EconomyManager
  listeners/  NexusCoreListener, NexusTabletListener, NexusGUIListener
  gui/        NexusGUIManager + holders/
  commands/   NexusCommand
  model/      NexusNetwork, AccessLevel
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

## Notes techniques

- L'ajout de membre se fait via une saisie chat simple (tape le pseudo)
  plutôt qu'une anvil GUI, pour rester robuste sans dépendance NMS.
- Chaque page de stockage utilise une inventaire 54 slots : 45 slots de
  stockage réel (0-44) + une rangée de navigation (45 = page précédente,
  49 = indicateur, 53 = page suivante).
- Vault est en **softdepend** : si absent, tous les coûts sont ignorés
  automatiquement (aucune erreur).

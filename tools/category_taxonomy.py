"""Single source of truth for the map's canonical categories.

The map has exactly SIX main filter categories (the chips). Pipeline agents
emit many finer-grained categories, and the same fine category can hold
different real object types (e.g. INDUSTRIAL_DISRUPTION covers both an oil
refinery and a tractor plant). So canonicalization is keyword-aware.

Keyword promotion is intentionally conservative: it only rescues the two
cross-cutting cases that the fine categories get wrong -- oil infrastructure
scattered across INDUSTRIAL/LOGISTICS/UNCLEAR, and named tankers mislabeled as
oil. Everything else trusts the fine category's family (CATEGORY_FALLBACK), so
a warship damaged "in port" stays naval instead of being pulled to infra.

`canonical_category()` is used by:
  - tools/canonicalize-categories.py  (fixes the whole dataset in the pipeline)
  - tools/import-weekly-input.py       (canonicalizes each new weekly entry)
Keep the six ids in sync with MapScreen `categories` and OsintViewModel.
"""

import re

# The six canonical map categories (chip ids). OIL keeps the legacy id
# FUEL_SUPPLY_DISRUPTION so existing marker colouring / shadow-fleet checks and
# the app enum stay valid; only the chip LABEL becomes "Oil / Нафта".
OIL = "FUEL_SUPPLY_DISRUPTION"
SHADOW_FLEET = "SHADOW_FLEET_DISRUPTION"
NAVAL_FLEET = "MARITIME_ASSET_DISRUPTION"
MILITARY = "MILITARY_ASSET_DISRUPTION"
INDUSTRIAL = "INDUSTRIAL_DISRUPTION"
INFRASTRUCTURE = "INFRASTRUCTURE_DISRUPTION"

CANONICAL = {OIL, SHADOW_FLEET, NAVAL_FLEET, MILITARY, INDUSTRIAL, INFRASTRUCTURE}

# Fallback family for a fine-grained category when keywords are inconclusive.
CATEGORY_FALLBACK = {
    OIL: OIL,
    "ENERGY_EXPORT_DISRUPTION": OIL,
    "FUEL_SUPPLY_DISRUPTION": OIL,
    SHADOW_FLEET: SHADOW_FLEET,
    "SHADOW_FLEET_SANCTIONS": SHADOW_FLEET,
    "VESSEL_SEIZURE_OR_DETENTION": SHADOW_FLEET,
    "VESSEL_DEREGISTRATION": SHADOW_FLEET,
    NAVAL_FLEET: NAVAL_FLEET,
    "NAVAL_VESSEL_DAMAGE": NAVAL_FLEET,
    "NAVAL_VESSEL_LOSS": NAVAL_FLEET,
    MILITARY: MILITARY,
    "MILITARY_INFRASTRUCTURE_DISRUPTION": MILITARY,
    "MILITARY_OR_INFRASTRUCTURE_STRIKE_UNCLEAR": MILITARY,
    "AIRFIELD_DISRUPTION": MILITARY,
    "AIRFIELD_OR_MILITARY_INFRASTRUCTURE_DISRUPTION": MILITARY,
    "AIRFIELD_AMMUNITION_STORAGE_DISRUPTION": MILITARY,
    "AIRFIELD_SUPPORT_INFRASTRUCTURE_DISRUPTION": MILITARY,
    "AMMUNITION_DEPOT_DISRUPTION": MILITARY,
    "GRAU_ARSENAL_DISRUPTION": MILITARY,
    "STRATEGIC_AVIATION_BASE_DISRUPTION": MILITARY,
    "STRATEGIC_AVIATION_ASSET_DAMAGE": MILITARY,
    "STRATEGIC_AVIATION_DISRUPTION": MILITARY,
    INDUSTRIAL: INDUSTRIAL,
    "DEFENSE_INDUSTRIAL_DISRUPTION": INDUSTRIAL,
    INFRASTRUCTURE: INFRASTRUCTURE,
    "PORT_LOGISTICS_DISRUPTION": INFRASTRUCTURE,
    "LOGISTICS_PRESSURE": INFRASTRUCTURE,
    "REGIONAL_FISCAL_STRESS": INFRASTRUCTURE,
    "PUBLIC_CASUALTY_DOCUMENTATION": INFRASTRUCTURE,
}

# Named tanker / cargo vessel -> shadow fleet even if it carries oil. Checked
# before the oil rule. Kept tight to avoid catching land oil objects.
_TANKER = re.compile(
    "танкер|tanker|нафтоналивн|газовоз|gas carrier|балкер|bulk.?carrier|"
    "тіньов(ого|ий|і) флот|shadow.?fleet",
    re.I)

# Oil / petroleum infrastructure -> OIL, regardless of the fine category it
# arrived in (refineries tagged INDUSTRIAL, pumping stations tagged LOGISTICS,
# Caspian oil platforms tagged UNCLEAR, etc.). Deliberately excludes tankers.
_OIL = re.compile(
    "нафтопереробн|нафтобаз|нафтопровід|нафтопрод|нафтотермінал|нафтосховищ|"
    "нафтовидобув|нафтова платформ|нафтогаз|нафтоперекачувальн|нафтоперекачув|"
    "нафтозавод|нафтопереробк|"
    "\\bнпз\\b|\\bнпс\\b|\\bлвдс\\b|refiner|petroleum|"
    "oil (depot|platform|terminal|pumping|refin|reservoir|storage|field|pipeline)|"
    "перекачувальн(а|у|ої| ) (станц|насос)|pumping station|нафтовий термінал|"
    "резервуар|мазут|бензин|дизельн(е|ого) (пальн|палив)|паливн(ий|ого) (склад|термінал|резервуар)|"
    "пальн(е|ого|им)|"
    "нафт(а|и|у|ою|і|ового|овий|ова|ове|опереробн)",
    re.I)


def _text(event):
    return " ".join(str(event.get(k) or "") for k in ("titleUk", "titleEn", "impactTags"))


def canonical_category(category, event=None, text=None):
    """Return one of the six canonical categories for an event.

    Pass either `event` (dict with titleUk/titleEn/impactTags) or `text`.
    A named tanker is shadow fleet even if it carries oil; oil infrastructure
    is promoted to OIL from whatever fine category it arrived in; everything
    else keeps its fine category's canonical family.
    """
    category = str(category or "")
    haystack = text if text is not None else (_text(event) if event else "")

    if _TANKER.search(haystack):
        return SHADOW_FLEET
    if _OIL.search(haystack):
        return OIL
    if category in CANONICAL:
        return category
    return CATEGORY_FALLBACK.get(category, MILITARY)

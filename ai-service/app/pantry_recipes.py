"""Deterministic, pantry-verified answers for FoodLens chat.

Never ask a language model to decide whether an ingredient exists in the pantry.
All recipe matches require verified names, usable quantities and compatible units.
This module does not update or reserve stock.
"""
from __future__ import annotations

from datetime import date
import re
from typing import Any


_ALIASES = {
    "eggs": "egg", "egg": "egg",
    "tomatoes": "tomato", "tomato": "tomato", "cherry tomatoes": "tomato",
    "potatoes": "potato", "potato": "potato",
    "vegetables": "vegetables", "mixed veggies": "mixed vegetables",
    "mixed vegetable": "mixed vegetables", "mixed vegetables": "mixed vegetables",
    "rotis": "roti", "roti": "roti",
    "idlis": "idli", "idli": "idli",
    "naans": "naan", "naan": "naan", "naan bread": "naan",
    "onions": "onion", "onion": "onion",
    "cucumbers": "cucumber", "cucumber": "cucumber",
    "apples": "apple", "apple": "apple",
    "bananas": "banana", "banana": "banana",
}
_UNIT_MAP = {
    "g": ("g", 1.0), "gram": ("g", 1.0), "grams": ("g", 1.0),
    "kg": ("g", 1000.0), "kilogram": ("g", 1000.0), "kilograms": ("g", 1000.0),
    "ml": ("ml", 1.0), "milliliter": ("ml", 1.0), "milliliters": ("ml", 1.0),
    "millilitre": ("ml", 1.0), "millilitres": ("ml", 1.0),
    "l": ("ml", 1000.0), "liter": ("ml", 1000.0), "liters": ("ml", 1000.0),
    "litre": ("ml", 1000.0), "litres": ("ml", 1000.0),
    "pcs": ("pcs", 1.0), "pc": ("pcs", 1.0), "piece": ("pcs", 1.0),
    "pieces": ("pcs", 1.0), "count": ("pcs", 1.0), "unit": ("pcs", 1.0),
    "units": ("pcs", 1.0), "nos": ("pcs", 1.0),
}
_INGREDIENT = re.compile(
    r"^(.+?)\s+(\d+(?:\.\d+)?)\s*([A-Za-z]+)\s*$", re.IGNORECASE
)
_RECIPE_WORDS = re.compile(r"\b(cook|cooking|recipe|recipes|prepare|dinner|lunch|breakfast|dishes|dish|meal ideas|what to eat)\b")
_PANTRY_WORDS = re.compile(
    r"\b(pantry|fridge|refrigerator|available|in stock|what i have|what do i have|i have|my ingredients|ingredients i have|on hand|using|with my|from my|only|use up|what can i|what should i|suggest a recipe|suggest me a recipe)\b"
)
_PROTEIN_WORDS = re.compile(r"\b(high[- ]protein|more protein|protein rich)\b")
_LOWCAL_WORDS = re.compile(r"\b(low[- ]calorie|less calories|under \d+ (?:kcal|calories))\b")

_STEPS = {
    "egg and tomato scramble": "Dice the tomatoes. Beat the eggs with the milk. Cook the tomatoes in a non-stick pan, add the eggs, and stir until set.",
    "chicken rice bowl": "Cook the rice. Heat the chicken curry thoroughly. Assemble with the salad.",
    "paneer veg bowl": "Cook the rice. Cook the paneer and mixed vegetables in a non-stick pan. Serve together.",
    "idli sambar breakfast": "Steam the idlis. Heat the sambar and serve them together.",
    "chana masala with roti": "Heat the chana masala and rotis. Slice the tomatoes and serve alongside.",
    "salmon and vegetable bowl": "Cook the rice. Cook the salmon thoroughly and steam the mixed vegetables. Serve together.",
}


def _canon(value: Any) -> str:
    text = re.sub(r"[^a-z0-9 ]+", " ", str(value or "").lower())
    text = " ".join(text.split())
    return _ALIASES.get(text, text)


def _today(context: dict[str, Any]) -> date:
    try:
        return date.fromisoformat(str(context.get("date")))
    except (ValueError, TypeError):
        return date.today()


def _quantity(number: Any, unit: Any):
    try:
        amount = float(number)
    except (ValueError, TypeError):
        return None
    if amount <= 0 or amount == float("inf") or amount != amount:
        return None
    conv = _UNIT_MAP.get(str(unit or "").strip().lower())
    return (conv[0], amount * conv[1]) if conv else None


def _inventory(context: dict[str, Any]):
    today = _today(context)
    stock: dict[str, dict[str, float]] = {}
    visible: list[str] = []
    expired: list[str] = []
    for item in context.get("pantry", []):
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or "").strip()
        if not name:
            continue
        unit_qty = _quantity(item.get("quantity"), item.get("unit"))
        if not unit_qty:
            continue
        expiry = item.get("expiryDate")
        if expiry:
            try:
                if date.fromisoformat(str(expiry)) < today:
                    expired.append(name)
                    continue
            except ValueError:
                # Don't certify an item with an unparseable expiry date.
                continue
        unit, qty = unit_qty
        stock.setdefault(_canon(name), {})[unit] = stock.setdefault(_canon(name), {}).get(unit, 0) + qty
        displayed = f"{name} ({item['quantity']} {item.get('unit', '')})"
        visible.append(displayed)
    return stock, visible, expired


def _needed(item: Any):
    if not isinstance(item, str):
        return None
    match = _INGREDIENT.match(item.strip())
    if not match:
        return None
    name, amount, unit = match.groups()
    converted = _quantity(amount, unit)
    if not converted:
        return None
    return (_canon(name), converted[0], converted[1])


def _diet_allows(recipe: dict[str, Any], context: dict[str, Any]) -> bool:
    pref = str(context.get("dietPreference") or "").lower().replace("-", " ")
    if not pref or "non vegetarian" in pref or "omnivore" in pref:
        return True
    ingredients = recipe.get("ingredients") or []
    listed = [_needed(x) for x in ingredients]
    names = [x[0] for x in listed if x]
    meat = {"chicken", "chicken breast", "chicken curry", "fish", "salmon", "beef", "pork", "mutton", "lamb", "prawn", "shrimp", "turkey"}
    dairy_eggs = {"egg", "milk", "paneer", "cheese", "butter", "ghee", "curd", "yogurt", "yoghurt", "raita", "cream"}
    if "vegan" in pref or "plant based" in pref:
        return not any(any(w == name or w in name.split() for w in meat | dairy_eggs) for name in names)
    if "vegetarian" in pref:
        # Conservative Indian vegetarian definition; unless explicitly ovo-vegetarian.
        avoid = meat if "ovo" in pref else meat | {"egg"}
        return not any(any(w == name or w in name.split() for w in avoid) for name in names)
    return True


def _recipe_match(recipe: dict[str, Any], stock: dict[str, dict[str, float]]) -> bool:
    ingredients = recipe.get("ingredients")
    if not isinstance(ingredients, list) or not ingredients:
        return False
    used: dict[tuple[str, str], float] = {}
    for ingredient in ingredients:
        need = _needed(ingredient)
        if not need:
            # Unknown units, amounts or ingredients are NOT assumed to exist.
            return False
        name, unit, amount = need
        key = (name, unit)
        used[key] = used.get(key, 0) + amount
        if stock.get(name, {}).get(unit, 0) + 1e-7 < used[key]:
            return False
    return True


# Constructive local recipe templates. Unlike a saved-recipe lookup, these produce
# usable cooking ideas from a subset of confirmed pantry ingredient *names*.
# No LLM, water, salt, butter, or imaginary pantry staples are introduced.
# Because imported pantry units can be nonsensical (e.g. milk in pcs), templates
# intentionally do not claim that sufficient quantities have been verified.
_CREATIVE_RECIPES = [
    ("Sweet Milk Toast", ("bread", "milk", "sugar", "oil"),
     "Spread a tiny amount of oil in a pan and toast the bread lightly on both sides. "
     "Stir a little sugar into the milk. Spoon the sweetened milk over the toast and serve."),
    ("Sweet Milk Bread", ("bread", "milk", "sugar"),
     "Stir sugar into the milk. Cut or tear the bread into pieces, briefly soak them in the sweetened milk and serve."),
    ("Sweet Pan Toast", ("bread", "oil", "sugar"),
     "Lightly toast the bread with a little oil in a pan. Sprinkle a little sugar over it before serving."),
    ("Milk-Dipped Pan Toast", ("bread", "milk", "oil"),
     "Toast the bread in a pan with a very small amount of oil until golden. Briefly dip each piece in milk just before serving."),
    ("Warm Bread and Milk Bowl", ("bread", "milk", "sugar"),
     "Warm the milk gently, stir in sugar, and add torn bread. Let it soften briefly, then serve warm."),
    ("Egg and Tomato Scramble", ("egg", "tomato", "milk"),
     "Chop the tomato, beat the egg with a little milk, and gently cook together in a nonstick pan until the egg is fully set."),
    ("Egg and Tomato Scramble", ("egg", "tomato"),
     "Chop the tomato and cook it briefly in a nonstick pan. Add beaten egg and stir until fully set."),
    ("Eggy Bread", ("egg", "bread", "milk"),
     "Beat the egg with a splash of milk. Dip the bread in the mixture, then cook in a nonstick pan until both sides and the egg are fully cooked."),
    ("Eggy Bread", ("egg", "bread"),
     "Beat the egg and coat the bread. Cook both sides in a nonstick pan until the egg is fully set."),
    ("Sweet Rice Pudding", ("rice", "milk", "sugar"),
     "Use already cooked rice. Simmer it gently in the milk with sugar, stirring until creamy and heated through."),
    ("Cucumber Tomato Salad", ("cucumber", "tomato", "onion"),
     "Wash and chop the cucumber and tomato, thinly slice the onion, then toss together."),
    ("Cucumber Tomato Salad", ("cucumber", "tomato"),
     "Wash and chop the cucumber and tomato, then mix and serve."),
    ("Tomato Toast", ("bread", "tomato"),
     "Toast the bread in a dry pan. Slice the tomato and arrange on the toast."),
    ("Sweet Banana Milk", ("milk", "banana", "sugar"),
     "Mash the banana, mix it thoroughly into the milk and sweeten with a little sugar."),
    ("Banana Milk", ("milk", "banana"),
     "Mash the banana and stir it into the milk until well combined."),
    ("Pan-Toasted Bread", ("bread", "oil"),
     "Lightly coat a pan with oil and toast the bread on both sides until golden."),
    ("Sweetened Milk", ("milk", "sugar"),
     "Stir a little sugar into the milk until dissolved. Serve chilled or gently warm."),
]

# Do not count near-identical variations of the same dish as different
# meal ideas. These families select one reasonable representative.
_RECIPE_FAMILY = {
    "Milk-Dipped Pan Toast": "milk-toast",
    "Sweet Milk Toast": "milk-toast",
    "Warm Bread and Milk Bowl": "sweet-milk-bread",
    "Sweet Milk Bread": "sweet-milk-bread",
    "Pan-Toasted Bread": "sweet-pan-toast",
    "Sweet Pan Toast": "sweet-pan-toast",
}

# Measured amounts are *proposed cooking portions*, not inferred pantry stock.
# Persist only via the authenticated Spring endpoint, which rechecks stock.
_PORTIONS: dict[str, list[tuple[str, float, str]]] = {
    "Sweet Milk Toast": [("bread", 1, "pcs"), ("milk", 120, "ml"), ("sugar", 10, "g"), ("oil", 5, "ml")],
    "Sweet Milk Bread": [("bread", 1, "pcs"), ("milk", 150, "ml"), ("sugar", 10, "g")],
    "Sweet Pan Toast": [("bread", 1, "pcs"), ("oil", 5, "ml"), ("sugar", 10, "g")],
    "Sweetened Milk": [("milk", 200, "ml"), ("sugar", 10, "g")],
    "Egg and Tomato Scramble": [("egg", 2, "pcs"), ("tomato", 1, "pcs")],
    "Eggy Bread": [("egg", 1, "pcs"), ("bread", 1, "pcs")],
    "Tomato Toast": [("bread", 1, "pcs"), ("tomato", 1, "pcs")],
    "Cucumber Tomato Salad": [("cucumber", 1, "pcs"), ("tomato", 1, "pcs")],
    "Banana Milk": [("banana", 1, "pcs"), ("milk", 200, "ml")],
    "Sweet Banana Milk": [("banana", 1, "pcs"), ("milk", 200, "ml"), ("sugar", 10, "g")],
}


def recipe_card_for_answer(answer: str, context: dict[str, Any]) -> dict[str, Any] | None:
    """Offer a recipe card only for trusted deterministic template responses.

    A chatbot's arbitrary text is never converted into a saveable recipe.
    """
    prefix = 'Fresh recipe idea (not from saved Recipes): '
    if not answer.startswith(prefix):
        return None
    title = answer[len(prefix):].split('\n', 1)[0].strip()
    stock, _, _ = _inventory(context)
    names = {_template_name(name) for name in stock}
    for candidate, required, steps in _CREATIVE_RECIPES:
        if title != candidate or not set(required).issubset(names):
            continue
        proposed = _PORTIONS.get(title)
        if not proposed or not set(required).issubset({i[0] for i in proposed}):
            # Don't offer a misleading card with guessed quantities.
            return None
        ingredients = [{'name': name.title(), 'quantity': qty, 'unit': unit}
                       for name, qty, unit in proposed]
        # Resolve common variants (e.g. cow milk) but NEVER conflate a
        # liquid stored in pieces with ml or infer g from pieces.
        measured: dict[str, dict[str, float]] = {}
        for canonical, units in stock.items():
            dest = measured.setdefault(_template_name(canonical), {})
            for unit, amount in units.items():
                dest[unit] = dest.get(unit, 0) + amount
        verified = all(measured.get(name, {}).get(unit, 0) >= qty
                       for name, qty, unit in proposed)
        warnings = [] if verified else [
            'Suggested portions are not verified against your recorded quantities. '
            'Correct pantry units and confirm amounts before cooking.'
        ]
        return {
            'name': title,
            'ingredients': ingredients,
            'instructions': steps,
            'servings': 1,
            'prepTimeMinutes': 5,
            'cookTimeMinutes': 10 if 'Toast' in title or 'Scramble' in title else 5,
            'cuisine': 'Pantry-inspired',
            'dietType': 'Omnivore' if 'Egg' in title else 'Vegetarian',
            'quantityVerified': verified,
            'warnings': warnings,
            'source': 'foodlens:curated-pantry'
        }
    return None


# Normalize a small number of common variants for templates only. Never use
# fuzzy matching to silently treat different ingredients as interchangeable.
_TEMPLATE_ALIASES = {
    "cooking oil": "oil", "vegetable oil": "oil", "olive oil": "oil",
    "bread slices": "bread", "sliced bread": "bread", "white bread": "bread",
    "whole wheat bread": "bread", "wholemeal bread": "bread",
    "cow milk": "milk", "whole milk": "milk", "dairy milk": "milk",
    "white sugar": "sugar", "granulated sugar": "sugar",
    "cooked rice": "rice", "white rice": "rice", "brown rice": "rice",
}


def _template_name(name: str) -> str:
    canonical = _canon(name)
    return _TEMPLATE_ALIASES.get(canonical, canonical)


def _history_messages(history: Any) -> list[tuple[str, str]]:
    """Accept both FastAPI ChatTurn objects and serializable dict messages."""
    result = []
    for turn in (history or [])[-48:]:
        if isinstance(turn, dict):
            role, content = turn.get("role"), turn.get("content")
        else:
            role, content = getattr(turn, "role", None), getattr(turn, "content", None)
        if role in {"user", "assistant"} and isinstance(content, str):
            result.append((role, content))
    return result


def _previous_recipe_titles(history: Any, titles: list[str]) -> set[str]:
    """Use only assistant replies: user-written titles do not count as suggestions."""
    # A count answer can *mention* future recipe titles without recommending
    # them. Do not mistake those names for previously served dishes.
    replies = "\n".join(
        content.casefold() for role, content in _history_messages(history)
        if role == "assistant" and not content.startswith((
            "I can offer ", "I have already suggested "
        ))
    )
    return {title.casefold() for title in titles if title.casefold() in replies}


def _is_another_recipe(q: str) -> bool:
    return bool(re.search(
        r"\b(?:another|different|other|alternative|next|more)\b.{0,65}\b(?:recipe|dish|meal|idea|option)s?\b|"
        r"\b(?:recipe|dish|meal|idea|option)s?\b.{0,50}\b(?:another|different|other|alternative|next|more)\b|"
        r"\bwhat else (?:can i (?:make|cook)|could i (?:make|cook))\b|"
        r"\bnot this one\b|\bsomething else\b",
        q
    ))


def _should_apply_pantry_constraint(history: Any) -> bool:
    """Carry explicit pantry-only requirements forward into follow-up turns."""
    for role, content in reversed(_history_messages(history)):
        if role == "user" and (_PANTRY_WORDS.search(content.lower()) or
                               re.search(r"\b(?:exactly (?:these|those)|only these|only those)\b", content.lower())):
            return True
        if role == "assistant" and ("saved pantry only" in content.lower() or
                                    "ingredients from your saved pantry" in content.lower() or
                                    "recipes verified against your saved pantry" in content.lower()):
            return True
    return False


def _invent_from_pantry(context: dict[str, Any], question: str, history: Any = None,
                        *, exclude_titles: set[str] | None = None) -> str | None:
    """Produce a verified-ingredient *idea*, not a verified-quantity recipe.

    Template instructions are fixed and audited for out-of-pantry ingredients.
    For unrecognized combinations we decline rather than hallucinate a recipe.
    """
    stock, visible, _ = _inventory(context)
    if not visible:
        return None
    canonical_names = {_template_name(name) for name in stock}
    if not canonical_names:
        return None
    vegan = "vegan" in str(context.get("dietPreference", "")).lower()
    vegetarian = "vegetarian" in str(context.get("dietPreference", "")).lower()
    candidates = []
    for title, required, steps in _CREATIVE_RECIPES:
        required_set = set(required)
        if not required_set.issubset(canonical_names):
            continue
        if title not in _PORTIONS:
            continue
        # Don't silently "repair" an incompatible saved recipe by generating
        # a different recipe with the same name and fewer required ingredients.
        if any(_canon(x.get("name")) == _canon(title) for x in context.get("recipes", []) if isinstance(x, dict)):
            continue
        if vegan and (required_set & {"milk", "egg"}):
            continue
        if vegetarian and "ovo" not in str(context.get("dietPreference", "")).lower() and "egg" in required_set:
            continue
        candidates.append((title, required, steps))
    if not candidates:
        return None
    # Check hard quantity limits for measurable items. Legacy incorrectly
    # recorded milk/oil/sugar in "pcs" may produce *idea only* suggestions,
    # never quantity-verified ones. Eggs/tomatoes etc must meet their amounts.
    measured = {}
    for canonical, units in stock.items():
        slot = measured.setdefault(_template_name(canonical), {})
        for unit, amount in units.items():
            slot[unit] = slot.get(unit, 0) + amount
    def enough_for_idea(candidate):
        title, required, _ = candidate
        proposed = _PORTIONS.get(title)
        if not proposed:
            return True  # Legacy idea with no measured card, explicitly unverified.
        if not set(required).issubset({part[0] for part in proposed}):
            return False
        for name, qty, unit in proposed:
            available = measured.get(name, {})
            if available.get(unit, 0) >= qty:
                continue
            if name in {"milk", "oil", "sugar"} and available.get("pcs", 0) > 0:
                continue
            return False
        return True
    candidates = [c for c in candidates if enough_for_idea(c)]
    if not candidates:
        return None
    # Skip repeated near-duplicates once a related technique was served.
    # Keep first representative from each family in priority order.
    prior = _previous_recipe_titles(history, [x[0] for x in candidates])
    family_by_lower = {k.casefold(): v for k, v in _RECIPE_FAMILY.items()}
    prior_families = {family_by_lower.get(t, t) for t in prior}
    candidates = [c for c in candidates if family_by_lower.get(c[0].casefold(),
                  c[0].casefold()) not in prior_families]
    # Prefer more useful ingredients, but never replay a previous assistant suggestion
    # when the user asks for a different recipe. This is determined from actual chat history.
    candidates.sort(key=lambda tpl: len(tpl[1]), reverse=True)
    if exclude_titles:
        candidates = [tpl for tpl in candidates if tpl[0].casefold() not in exclude_titles]
    already = _previous_recipe_titles(history, [tpl[0] for tpl in candidates])
    candidates = [tpl for tpl in candidates if tpl[0].casefold() not in already]
    if not candidates:
        return None
    title, required, steps = candidates[0]
    used = ", ".join(required)
    answer = (
        f"Fresh recipe idea (not from saved Recipes): {title}\n"
        f"Ingredients from your saved pantry only: {used}.\n"
        f"Steps: {steps}\n"
        "No extra ingredients or pantry staples are assumed. "
        "Check ingredient amounts before cooking; this is a recipe idea, not a verified-quantity match."
    )
    # Don't pretend that milk/oil/sugar measured in pieces is a usable volume.
    odd_units = []
    for item in context.get("pantry", []):
        if not isinstance(item, dict):
            continue
        if _template_name(item.get("name", "")) in required and str(item.get("unit", "")).lower() in {"pcs", "pc", "piece", "pieces"}:
            if _template_name(item.get("name", "")) in {"milk", "oil", "sugar"}:
                odd_units.append(str(item.get("name")))
    if odd_units:
        answer += "\nPantry units need correction: " + ", ".join(dict.fromkeys(odd_units)) + " listed as pieces; use ml or g for accurate portions."
    return answer


def _eligible_recipe_options(context: dict[str, Any]) -> list[tuple[str, str]]:
    """Return distinct food ideas supported by pantry items, not an exhaustive recipe search.

    A saved recipe must pass exact quantity/unit checks; creative templates require
    available ingredient names, with quantity verification explicitly left pending.
    """
    stock, _, _ = _inventory(context)
    if not stock:
        return []
    options: list[tuple[str, str]] = []
    seen: set[str] = set()
    for recipe in context.get("recipes", []):
        if (isinstance(recipe, dict) and _diet_allows(recipe, context)
                and _recipe_match(recipe, stock)):
            title = str(recipe.get("name") or "").strip()
            if title and title.casefold() not in seen:
                seen.add(title.casefold())
                options.append((title, "saved and quantity-verified"))
    names = {_template_name(name) for name in stock}
    preference = str(context.get("dietPreference") or "").lower()
    seen_families: set[str] = set()
    for title, needed, _ in _CREATIVE_RECIPES:
        family = _RECIPE_FAMILY.get(title, title)
        if family in seen_families:
            continue
        if not set(needed).issubset(names):
            continue
        if title not in _PORTIONS:
            continue
        if any(_canon(x.get("name")) == _canon(title) for x in context.get("recipes", []) if isinstance(x, dict)):
            continue
        if "vegan" in preference and set(needed) & {"milk", "egg"}:
            continue
        if "vegetarian" in preference and "ovo" not in preference and "egg" in needed:
            continue
        seen_families.add(family)
        if title.casefold() not in seen:
            seen.add(title.casefold())
            options.append((title, "pantry-only idea, amounts unverified"))
    return options


def _remaining_recipe_answer(context: dict[str, Any], history: Any) -> str:
    options = _eligible_recipe_options(context)
    if not options:
        return ("I can't verify a pantry-only recipe from the available data. "
                "Please check your pantry quantities or add ingredients. "
                "I won't invent ingredients.")
    titles = [title for title, _ in options]
    suggested = _previous_recipe_titles(history, titles)
    remaining = [(title, verification) for title, verification in options
                 if title.casefold() not in suggested]
    if remaining:
        shown = ", ".join(title for title, _ in remaining[:6])
        suffix = " ..." if len(remaining) > 6 else ""
        msg = (f"I can offer {len(remaining)} more distinct pantry-only idea(s) "
               f"from my current saved recipes and built-in templates, "
               f"in addition to {len(suggested)} already mentioned. "
               f"Remaining options: {shown}{suffix}. "
               "These are not all possible recipes, and template quantities are unverified.")
    else:
        msg = (f"I have already suggested the {len(suggested)} distinct matching "
               "recipe idea(s) in my current catalog. "
               "I won't invent another dish or assume missing ingredients. "
               "You can add pantry items or correct quantities for more options.")
    if context.get("pantryTruncated") or context.get("recipesTruncated"):
        msg += " This count covers only the saved items that were provided."
    return msg


def _asks_recipe_count(q: str) -> bool:
    return bool(re.search(
        r"\bhow many\b.{0,90}\b(?:recipe|recipes|dish|dishes|ideas|options)\b|"
        r"\bhow many\b.{0,45}\b(?:more|left|remaining)\b|"
        r"\b(?:recipe|recipes|dish|dishes|ideas|options)\b.{0,70}\b(?:how many|left|remaining|count)\b|"
        r"\b(?:any|are there)\b.{0,15}\b(?:more|other)\b.{0,45}\b(?:recipes|dishes|ideas)\b|"
        r"\b(?:is this it|is that it|is this all|is that all|are these all|nothing else|no more recipes|all the recipes)\b",
        q,
    ))


def _recipe_answer(question: str, context: dict[str, Any], history: Any = None, *, another: bool = False) -> str:
    stock, visible, expired = _inventory(context)
    if not visible:
        if expired:
            return "I found no usable items in your saved pantry (some entries are past their expiry dates). Add or update pantry items first. I won't invent ingredients."
        return "Your saved pantry has no usable ingredients with quantities. Add them in Pantry first; I won't guess what you have."

    recipes = [r for r in context.get("recipes", []) if isinstance(r, dict)]
    matched = [r for r in recipes if _diet_allows(r, context) and _recipe_match(r, stock)]
    if another:
        # A follow-up is not a reset: never repeat an earlier recipe.
        seen = _previous_recipe_titles(history, [str(r.get("name") or "") for r in matched])
        matched = [r for r in matched if str(r.get("name") or "").casefold() not in seen]
    if not matched:
        creative = _invent_from_pantry(context, question, history)
        if creative:
            return creative
        msg = ("I don't have another distinct pantry-only recipe I can verify from your current items."
               if another else
               "I can't verify a complete saved recipe or safe ingredient-only idea from your current pantry.")
        if context.get("pantryTruncated") or context.get("recipesTruncated"):
            msg += " Only part of your saved pantry/recipe catalog was available for this check."
        msg += "\nAvailable: " + ", ".join(visible[:16]) + ("…" if len(visible) > 16 else "")
        msg += "\nI won't add missing ingredients or assume you have pantry staples."
        return msg

    q = question.lower()
    if _PROTEIN_WORDS.search(q):
        matched.sort(key=lambda r: float(r.get("proteinPerServing") or 0), reverse=True)
    elif _LOWCAL_WORDS.search(q):
        matched.sort(key=lambda r: float(r.get("caloriesPerServing") or 99999))
    else:
        matched.sort(key=lambda r: (len(r.get("ingredients") or []), str(r.get("name"))), reverse=True)
    lines = ["Recipes verified against your saved pantry (including amounts and units):"]
    for i, recipe in enumerate(matched[:1] if another else matched[:3], 1):
        title = str(recipe.get("name") or "Saved recipe")
        minutes = recipe.get("minutes")
        details = f" ({minutes} min)" if isinstance(minutes, (float, int)) and minutes > 0 else ""
        lines.append(f"\n{i}. {title}{details}")
        lines.append("Ingredients: " + ", ".join(str(x) for x in recipe["ingredients"]))
        steps = _STEPS.get(_canon(title))
        if steps:
            lines.append("Steps: " + steps)
        else:
            lines.append("Steps: Open this saved recipe in Recipes. Follow its instructions only where they use the verified ingredients above; omit any extras.")
    lines.append("\nThese are alternatives, not a plan to cook all of them. Pantry stock has not been reserved or changed.")
    if context.get("pantryTruncated") or context.get("recipesTruncated"):
        lines.append("Only part of the saved pantry/recipe catalog was checked.")
    return "\n".join(lines)


def _inventory_answer(context: dict[str, Any]) -> str:
    _, visible, expired = _inventory(context)
    if not visible:
        return "I couldn't find usable ingredients with quantities in your saved Pantry."
    answer = "Available in your saved pantry: " + ", ".join(visible[:20]) + "."
    if len(visible) > 20 or context.get("pantryTruncated"):
        answer += " This is a partial list."
    if expired:
        answer += " Excluded past-expiry items: " + ", ".join(expired[:6]) + "."
    return answer


def _expiry_answer(context: dict[str, Any]) -> str:
    pantry = context.get("pantry", [])
    today = _today(context)
    entries = []
    for item in pantry:
        if not isinstance(item, dict) or not item.get("expiryDate"):
            continue
        try:
            d = date.fromisoformat(str(item["expiryDate"]))
        except ValueError:
            continue
        entries.append((d, str(item.get("name", "Item"))))
    if not entries:
        return "No valid expiry dates are recorded in your saved pantry."
    entries.sort()
    result = ["Saved pantry expiry dates (earliest first):"]
    for d, name in entries[:5]:
        status = " (past expiry)" if d < today else " (expires today)" if d == today else ""
        result.append(f"- {name}: {d.isoformat()}{status}")
    if context.get("pantryTruncated"):
        result.append("Only part of your pantry was checked.")
    return "\n".join(result)


def _saved_meals_answer(context: dict[str, Any]) -> str:
    totals = context.get("todaySavedMeals") or {}
    n = int(totals.get("savedMealCount") or 0)
    if not n:
        return "No meals are recorded in FoodLens for today. That doesn't necessarily mean you haven't eaten."
    return (
        f"FoodLens has {n} saved meal(s) today, totaling approximately "
        f"{totals.get('calories', 'unknown')} kcal and {totals.get('proteinGrams', 'unknown')} g protein "
        f"from the entries you saved. These are estimates."
    )


def deterministic_answer(question: str, context: dict[str, Any], history: Any = None) -> str | None:
    """Answer pantry-related questions without LLM hallucinations or latency.

    Returns None only when generic open-ended reasoning is actually useful.
    """
    q = " ".join(question.lower().strip().split())
    if not q:
        return None
    # Previous titles come from the user's current chat session. They are
    # used ONLY to avoid repeats, never as evidence of owned ingredients.
    prev = context.get("previousRecipeTitles") or []
    if isinstance(prev, list):
        synthetic = [{"role":"assistant", "content":"Fresh recipe idea: " + title}
                     for title in prev[:32] if isinstance(title, str) and 0 < len(title) <= 120]
        history = synthetic + list(history or [])
    if q.strip("!?., ") in {"hi", "hello", "hey", "hey there", "hello there", "good morning", "good evening", "good afternoon"}:
        return "Hello! Ask me what you can cook from your saved pantry, what expires first, or about your saved meals."
    if q.strip("!?., ") in {"thanks", "thank you", "thankyou"}:
        return "You're welcome!"
    if re.search(r"\b(expire|expires|expiring|expiry|expiration)\b", q):
        return _expiry_answer(context)
    if re.search(r"\b(what did i eat|what have i eaten|calories (?:today|so far)|nutrition today|my meals today|my food today)\b", q):
        return _saved_meals_answer(context)
    if re.search(r"\b(shopping list|grocery list|buy groceries)\b", q):
        return None
    # Count / exhaustion questions must be handled BEFORE the "another recipe"
    # detector. Otherwise "how many more recipes" invents another suggestion.
    if _asks_recipe_count(q):
        is_specific = bool(_PANTRY_WORDS.search(q) or
                           _RECIPE_WORDS.search(q) or
                           _should_apply_pantry_constraint(history))
        if is_specific:
            return _remaining_recipe_answer(context, history)
    # "Give another recipe" inherits the pantry-only restriction from the
    # conversation, not merely the current utterance. Do not send it to the LLM.
    another = _is_another_recipe(q)
    ambiguous_followup = bool(re.search(
        r"\b(?:another one|one more|something different|try a different one|next one)\b", q
    )) and _should_apply_pantry_constraint(history)
    if another or ambiguous_followup:
        if "without pantry" not in q and "ignore my pantry" not in q:
            return _recipe_answer(question, context, history, another=True)
    recipe_intent = bool(_RECIPE_WORDS.search(q) or re.search(r"\bwhat can i make\b|\bmake\b.+\b(with|from)\b|\bmeal ideas\b", q))
    if recipe_intent and (
        _PANTRY_WORDS.search(q)
        or q.startswith(("suggest a recipe", "give me a recipe", "recommend a recipe", "any recipes"))
        or "what can i cook" in q
    ):
        return _recipe_answer(question, context, history)
    if re.search(r"\b(what(?:'s| is) in my pantry|show my pantry|list my pantry|what ingredients do i have|what do i have in my pantry|what items do i have in my pantry|what items (?:are|do i have) in my pantry|do you know what items (?:i have|are) in my pantry)\b", q):
        return _inventory_answer(context)
    return None

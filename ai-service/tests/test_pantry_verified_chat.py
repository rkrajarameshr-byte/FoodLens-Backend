import asyncio
import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.pantry_recipes import deterministic_answer
from app.main import ChatRequest, chat_with_ollama

RECIPE = {
    'name': 'Egg and Tomato Scramble',
    'ingredients': ['Eggs 2 pieces', 'Tomatoes 2 pieces', 'Milk 30 ml'],
    'minutes': 15,
    'caloriesPerServing': 240,
    'proteinPerServing': 15,
}


def ctx(*, eggs=8, tomato=6, milk=1, milk_unit='L', expired=False):
    return {
        'date': '2026-10-10',
        'pantry': [
            {'name': 'Eggs', 'quantity': eggs, 'unit': 'pcs', 'expiryDate': '2026-10-14'},
            {'name': 'Tomatoes', 'quantity': tomato, 'unit': 'pieces', 'expiryDate': '2026-10-14'},
            {'name': 'Milk', 'quantity': milk, 'unit': milk_unit, 'expiryDate': '2026-10-09' if expired else '2026-10-11'},
        ],
        'recipes': [RECIPE],
    }


def test_available_panry_recipe_and_ingredients():
    a = deterministic_answer('What can I cook with my pantry?', ctx())
    assert 'Egg and Tomato Scramble' in a
    assert 'Eggs 2 pieces' in a and 'Milk 30 ml' in a
    assert 'verified' in a
    assert 'pan' in a.lower()


def test_no_missing_ingredients_sneak_in():
    a = deterministic_answer('Give me a recipe based on pantry items', ctx(milk=0))
    assert 'can\'t verify' in a
    assert 'Egg and Tomato Scramble' not in a
    assert 'Milk 30 ml' not in a


def test_not_enough_eggs():
    a = deterministic_answer('What can I cook with my pantry?', ctx(eggs=1))
    assert 'can\'t verify' in a


def test_dont_use_expired_food():
    a = deterministic_answer('What can I cook with my pantry?', ctx(expired=True))
    assert 'can\'t verify' in a
    assert 'Egg and Tomato Scramble' not in a


def test_unit_mismatch_no_guess():
    # Eggs 500 grams cannot be assumed to mean 2 actual eggs.
    data = ctx()
    data['pantry'][0]['unit'] = 'g'
    a = deterministic_answer('Give me a recipe from my pantry', data)
    assert 'can\'t verify' in a


def test_multiple_entries_total_quantity():
    data = ctx(eggs=1)
    data['pantry'].append({'name': 'Egg', 'quantity': 1, 'unit': 'piece'})
    assert 'Egg and Tomato Scramble' in deterministic_answer('what can i cook with my pantry', data)


def test_catalog_recipe_requires_every_item():
    data = ctx()
    data['recipes'].append({'name': 'Fake Curry', 'ingredients': ['Eggs 2 pieces', 'Unobtainium 3 g']})
    a = deterministic_answer('what can i cook with my pantry', data)
    assert 'Fake Curry' not in a


def test_unknown_recipe_unit_cannot_be_assumed():
    data = ctx()
    data['recipes'] = [{'name': 'Egg soup', 'ingredients': ['Eggs 2 portions']}]
    assert 'Egg soup' not in deterministic_answer('what can i cook with my pantry', data)


def test_only_valid_recipes_no_assumed_staples():
    data = ctx()
    data['recipes'].append({'name': 'Egg curry', 'ingredients': ['Eggs 2 pieces', 'Salt 3 g', 'Tomatoes 2 pieces']})
    a = deterministic_answer('Suggest a recipe using pantry ingredients only', data)
    assert 'Egg curry' not in a
    assert 'Egg and Tomato Scramble' in a


def test_expiry_fast_and_correct():
    a = deterministic_answer('Which items expire first?', ctx(expired=True))
    assert '2026-10-09 (past expiry)' in a


def test_saved_meals_fast():
    a = deterministic_answer('What did I eat today?', {'todaySavedMeals': {'savedMealCount': 2, 'calories': 460, 'proteinGrams': 23}})
    assert '2 saved meal(s)' in a and '460' in a


def test_chat_api_fast_path_not_call_ollama(monkeypatch):
    async def fail(*args, **kwargs):
        raise AssertionError('Ollama should not be called for verified pantry questions')
    import httpx
    monkeypatch.setattr(httpx.AsyncClient, 'post', fail)
    r = asyncio.run(chat_with_ollama(ChatRequest(message='Suggest a recipe from my pantry', context=ctx())))
    assert 'Egg and Tomato Scramble' in r.answer
    assert r.provider == 'foodlens:local'


def test_greeting_fast_path_not_call_ollama(monkeypatch):
    async def fail(*args, **kwargs):
        raise AssertionError('No LLM for greeting')
    import httpx
    monkeypatch.setattr(httpx.AsyncClient, 'post', fail)
    r = asyncio.run(chat_with_ollama(ChatRequest(message='hi', context={})))
    assert r.answer.startswith('Hello!')


def test_expired_nonpositive_or_unknown_units_excluded():
    data = ctx()
    data['pantry'][0]['quantity'] = -1
    assert 'can\'t verify' in deterministic_answer('what can i cook from my pantry', data)
    data['pantry'][0]['quantity'] = 8
    data['pantry'][0]['unit'] = 'handfuls'
    assert 'can\'t verify' in deterministic_answer('what can i cook from my pantry', data)


def test_diet_preference_restrictions():
    data = ctx()
    data['dietPreference'] = 'Vegetarian'
    assert 'Egg and Tomato Scramble' not in deterministic_answer('What can I cook with my pantry?', data)
    data['dietPreference'] = 'Ovo-vegetarian'
    assert 'Egg and Tomato Scramble' in deterministic_answer('What can I cook with my pantry?', data)
    data['dietPreference'] = 'Vegan'
    assert 'Egg and Tomato Scramble' not in deterministic_answer('What can I cook with my pantry?', data)


def test_make_shopping_list_does_not_trigger_pantry_recipes():
    assert deterministic_answer('Make a shopping list from my pantry', ctx()) is None


def test_flexible_phrasings_are_panry_grounded():
    data = ctx()
    for question in ('What should I prepare using my pantry?',
                     'Suggest a high-protein dinner with my available ingredients',
                     'What can I make from my pantry?',
                     'Give me a recipe based on available items in my pantry'):
        assert 'Egg and Tomato Scramble' in deterministic_answer(question, data), question


def test_explicit_expired_ingredient_doesnt_match():
    data = ctx()
    data['pantry'][2]['expiryDate'] = '2026-01-01'
    assert 'Egg and Tomato Scramble' not in deterministic_answer('Suggest recipe from pantry', data)

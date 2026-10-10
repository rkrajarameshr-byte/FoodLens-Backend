"""Runs via python3 -m unittest discover -s ai-service/tests -p test_creative_pantry.py"""
import unittest
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.pantry_recipes import deterministic_answer


def context(**kwargs):
    pantry=kwargs.pop('pantry', [dict(name=x, quantity=1, unit='pcs') for x in ('Oil','Bread','Milk','Sugar')])
    return {'date':'2026-10-10','pantry':pantry,'recipes':[],**kwargs}


class PantryChatTest(unittest.TestCase):
    def test_four_items_has_new_recipe(self):
        ans = deterministic_answer('exactly with these four create a recipe only these four',context())
        self.assertIn('Sweet Milk Toast',ans)
        self.assertIn('bread, milk, sugar, oil',ans)
        self.assertNotIn('I can\'t verify a complete saved recipe',ans)
        self.assertNotIn('water',ans.lower())
        self.assertNotIn('salt',ans.lower())

    def test_warning_invalid_pantry_units(self):
        ans = deterministic_answer('make a recipe using my pantry',context())
        self.assertIn('units need correction',ans)
        self.assertIn('Milk',ans)

    def test_not_spend_expired_milk(self):
        p=context()['pantry']
        p[2]['expiryDate']='2026-10-09'
        ans = deterministic_answer('make a recipe using my pantry',context(pantry=p))
        self.assertNotIn('Sweet Milk Toast',ans)
        self.assertNotIn('sweetened milk',ans)

    def test_no_invent_missing_oil(self):
        p=context()['pantry'][1:]
        ans = deterministic_answer('recipe using only my pantry',context(pantry=p))
        self.assertIn('Sweet Milk Bread',ans)
        self.assertNotIn('oil',ans.lower())

    def test_quick_pantry_inventory(self):
        a = deterministic_answer('what items do i have in my pantry?',context())
        self.assertIn('Oil (1 pcs)',a)
        self.assertIn('Sugar (1 pcs)',a)

    def test_avoid_false_positive(self):
        p=[{'name':'Bread','quantity':1,'unit':'pcs'}]
        a=deterministic_answer('give me a recipe using only my pantry',context(pantry=p))
        self.assertIn("can't verify",a.lower())
        self.assertNotIn('Sweet Milk Toast',a)

    def test_saved_qualified_recipes_still_work(self):
        ctx=context(pantry=[{'name':'Eggs','quantity':3,'unit':'pcs'}, {'name':'Tomatoes','quantity':2,'unit':'pieces'}, {'name':'Milk','quantity':200,'unit':'ml'}])
        ctx['recipes']=[{'name':'Egg and Tomato Scramble', 'ingredients':['Eggs 2 pieces','Tomatoes 2 pieces','Milk 30 ml']}]
        ans=deterministic_answer('what can i cook with my pantry',ctx)
        self.assertIn('Recipes verified',ans)
        self.assertIn('Egg and Tomato Scramble',ans)

    def test_dietary_restriction(self):
        ans=deterministic_answer('create a recipe from pantry',context(dietPreference='Vegan'))
        self.assertNotIn('Sweet Milk Toast',ans)

if __name__ == '__main__':
    unittest.main()

import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from app.pantry_recipes import deterministic_answer


def pantry():
    return {
        'date':'2026-10-10',
        'pantry':[
            {'name':'Oil','quantity':1,'unit':'pcs'},
            {'name':'Bread','quantity':1,'unit':'pcs'},
            {'name':'Milk','quantity':1,'unit':'pcs'},
            {'name':'Sugar','quantity':1,'unit':'pcs'},
        ],
        'recipes':[],
    }


def turn(role,content):return {'role':role,'content':content}


class RecipeFollowupTests(unittest.TestCase):
    def test_first_idea(self):
        text = deterministic_answer('Create a recipe with my pantry only',pantry())
        self.assertIn('Sweet Milk Toast',text)

    def test_generic_followup_does_not_use_llm_or_hallucinate(self):
        h=[turn('user','Create a recipe using only my pantry'),turn('assistant','Fresh recipe idea: Sweet Milk Toast')]
        text=deterministic_answer('give another recipe',pantry(),h)
        self.assertIsNotNone(text)
        self.assertNotIn('Sweet Milk Toast',text)
        self.assertIn('Sweet Milk Bread',text)
        for missing in ('pasta','chickpeas','garlic','onion','broth'):
            self.assertNotIn(missing,text.lower())

    def test_pantry_explicit_followup_not_repeat(self):
        h=[turn('user','use oil bread milk sugar only'),turn('assistant','Fresh recipe idea: Sweet Milk Toast')]
        text=deterministic_answer('give another recipe based on pantry items',pantry(),h)
        self.assertIn('Sweet Milk Bread',text)

    def test_three_alternatives_are_distinct(self):
        h=[turn('user','my pantry only'),turn('assistant','Sweet Milk Toast'),turn('user','give another recipe'),turn('assistant','Sweet Milk Bread')]
        text=deterministic_answer('another recipe',pantry(),h)
        self.assertIn('Sweet Pan Toast',text)

    def test_suggestion_not_reused_as_more(self):
        h=[turn('user','from my pantry'),turn('assistant','Sweet Milk Toast')]
        text=deterministic_answer('another one',pantry(),h)
        self.assertIn('Sweet Milk Bread',text)

    def test_no_extra_staples(self):
        h=[turn('assistant','Sweet Milk Toast')]
        text=deterministic_answer('another recipe',pantry(),h)
        for missing in ('salt','water','butter','cheese'):
            self.assertNotIn(missing,text.lower())

    def test_existing_pantry_words_still_list(self):
        self.assertIn('Bread (1 pcs)',deterministic_answer('What do I have in my pantry?',pantry()))

    def test_expired_items_not_suggested(self):
        data=pantry()
        data['pantry'][2]['expiryDate']='2026-10-09'
        h=[turn('assistant','Sweet Pan Toast')]
        text=deterministic_answer('another recipe based on pantry',data,h)
        self.assertNotIn('Sweet Milk Toast',text)
        self.assertNotIn('Sweet Milk Bread',text)

    def test_no_more_distinct_recipes(self):
        data={'date':'2026-10-10','pantry':[{'name':'Bread','quantity':1,'unit':'pcs'},{'name':'Tomato','quantity':1,'unit':'pcs'}], 'recipes':[]}
        h=[turn('user','only pantry'),turn('assistant','Fresh recipe idea: Tomato Toast')]
        text=deterministic_answer('another recipe',data,h)
        self.assertIn('another distinct pantry-only recipe',text)

    def test_previously_recipe_text_not_a_new_suggestion(self):
        h=[turn('user','I ate Sweet Milk Bread yesterday')]
        self.assertIn('Sweet Milk Toast',deterministic_answer('make a recipe from my pantry',pantry(),h))

    def test_saved_recipe_followup(self):
        data={'date':'2026-10-10','pantry':[{'name':'Eggs','quantity':3,'unit':'pcs'},{'name':'Tomatoes','quantity':2,'unit':'pieces'}],
              'recipes':[{'name':'Egg Scramble','ingredients':['Eggs 1 pcs']},{'name':'Tomato Omelette','ingredients':['Eggs 1 pcs','Tomatoes 2 pieces']} ]}
        h=[turn('assistant','Recipes verified against your saved pantry: Tomato Omelette')]
        text=deterministic_answer('give another recipe',data,h)
        self.assertNotIn('Tomato Omelette',text)
        self.assertIn('Egg Scramble',text)

    def test_generic_creative_still_works(self):
        data=pantry()
        self.assertIn('Sweet Milk Toast',deterministic_answer('Exactly with these four create a recipe only these four', data))

    def test_invalid_expired_no_recipe(self):
        data=pantry()
        for i in data['pantry']:
            i['expiryDate']='2026-10-09'
        answer=deterministic_answer('another recipe',data,[])
        self.assertIn('no usable items',answer)


if __name__=='__main__':unittest.main()

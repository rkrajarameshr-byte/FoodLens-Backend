import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from app.pantry_recipes import deterministic_answer, _eligible_recipe_options


def pantry():
    return {'date': '2026-10-10', 'pantry': [
        {'name': name, 'unit': 'pcs', 'quantity': 1}
        for name in ('Oil', 'Bread', 'Milk', 'Sugar')], 'recipes': []}


def chat_history():
    return [
        {'role': 'user', 'content': 'Give me a recipe using only the items currently available in my pantry.'},
        {'role': 'assistant', 'content': 'Fresh recipe idea (not from saved Recipes): Sweet Milk Toast'},
        {'role': 'user', 'content': 'give me another recipe'},
        {'role': 'assistant', 'content': 'Fresh recipe idea: Sweet Milk Bread'},
        {'role': 'user', 'content': 'how many more are recipes'},
        {'role': 'assistant', 'content': 'Fresh recipe idea: Sweet Pan Toast'},
    ]


class RecipeCountTests(unittest.TestCase):
    def test_nonstandard_count_question_does_not_generate_recipe(self):
        answer = deterministic_answer('how many more are recipes', pantry(), chat_history())
        self.assertIn('more distinct pantry-only', answer)
        self.assertIn('Remaining options:', answer)
        self.assertNotIn('Steps:', answer)

    def test_is_this_it_preserves_pantry_scope(self):
        answer = deterministic_answer('is this it', pantry(), chat_history())
        self.assertIn('more distinct pantry-only', answer)
        self.assertNotIn('loaf', answer.lower())
        self.assertNotIn('chickpea', answer.lower())

    def test_count_only_curated_templates_not_world_recipes(self):
        data = pantry()
        options = _eligible_recipe_options(data)
        answer = deterministic_answer('how many recipes do you have for my pantry', data)
        self.assertIn(f'{len(options)} more distinct', answer)
        self.assertIn('not all possible recipes', answer)

    def test_count_does_not_consume_unsuggested_recipes(self):
        data = pantry()
        h = [
            {'role': 'user','content':'using only my pantry suggest a recipe'},
            {'role': 'assistant','content':'Fresh recipe idea: Sweet Milk Toast'}
        ]
        count = deterministic_answer('how many more recipes', data, h)
        h += [{'role':'user','content':'how many more recipes'},
              {'role':'assistant','content':count}]
        nxt = deterministic_answer('give another recipe', data, h)
        self.assertIn('Sweet Milk Bread', nxt)

    def test_no_remaining_after_all_ideas_suggested(self):
        data = pantry()
        titles = [title for title, _ in _eligible_recipe_options(data)]
        history = [ {'role': 'user','content':'use only pantry'},
                    {'role': 'assistant','content':'; '.join(titles)} ]
        answer = deterministic_answer('is that all', data, history)
        self.assertIn('already suggested', answer)
        self.assertNotIn('Remaining options:', answer)

    def test_standalone_is_this_it_does_not_guess(self):
        self.assertIsNone(deterministic_answer('is this it', pantry(), []))

    def test_expired_ingredients_cannot_count(self):
        data = pantry()
        for item in data['pantry']:
            item['expiryDate']='2026-10-09'
        answer = deterministic_answer('how many recipes from my pantry', data)
        self.assertIn("can't verify", answer)


if __name__ == '__main__':
    unittest.main()

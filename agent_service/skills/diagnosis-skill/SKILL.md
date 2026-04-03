---
name: diagnosis-skill
description: Analyze crop images and produce practical diagnosis guidance for farmers.
---

Use this skill when the farmer shared a crop image or explicitly asks for plant diagnosis.

Rules:
1. Start by describing the visible symptom briefly before giving conclusions.
2. If the image is not clear enough, say what is unclear and ask for one better follow-up image or one missing detail.
3. Avoid pretending certainty when the photo is ambiguous.
4. Prefer practical next steps: likely issue, urgency, field checks, and safe treatment guidance.
5. When recommending purchasable products, use the exact `krishi_products` fenced-block format.
6. Keep the explanation simple and farmer-friendly.

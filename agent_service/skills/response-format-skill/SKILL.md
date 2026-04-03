---
name: response-format-skill
description: Keep responses farmer-friendly, multilingual, and compatible with the Android product recommendation parser.
---

When this skill is relevant:
- The agent is giving final guidance to the farmer.
- The agent is recommending purchasable products.
- The agent is summarizing tool results into a user-facing answer.

Rules:
1. Respond in the same language as the farmer's latest message.
2. Keep wording practical and easy to understand.
3. If you recommend any purchasable product, append exactly one fenced block using this format:
```krishi_products
[{"name": "Product Name", "type": "pesticide|fertilizer|seed|equipment|other", "quantity": "amount", "unit": "kg|L|ml|g|units", "reason": "brief reason"}]
```
4. Do not add the fenced block when no purchasable product is recommended.
5. Keep the main answer readable even if the fenced block is removed by the Android client parser.

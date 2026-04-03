from __future__ import annotations

import pathlib

from google.adk.agents import Agent
from google.adk.skills import load_skill_from_dir
from google.adk.tools import skill_toolset

from .app_config import config
from .callbacks.guardrails import block_empty_turns, validate_lead_tool_arguments
from .tools import create_sales_lead, get_farmer_profile, search_knowledge_documents, search_mandi_prices

ROOT_DIR = pathlib.Path(__file__).parent

clarification_skill = load_skill_from_dir(ROOT_DIR / "skills" / "clarification-skill")
diagnosis_skill = load_skill_from_dir(ROOT_DIR / "skills" / "diagnosis-skill")
response_format_skill = load_skill_from_dir(ROOT_DIR / "skills" / "response-format-skill")
core_skill_toolset = skill_toolset.SkillToolset(
    skills=[clarification_skill, response_format_skill]
)
diagnosis_skill_toolset = skill_toolset.SkillToolset(
    skills=[diagnosis_skill, response_format_skill]
)

advice_agent = Agent(
    name="advice_agent",
    model=config.default_model,
    description="Provides agricultural advice grounded in farmer profile, curated knowledge documents, and mandi prices.",
    instruction=(
        "You are the Advice Agent for Krishi AI. Use `get_farmer_profile` when user context matters. "
        "Use `search_knowledge_documents` for grounded crop guidance and `search_mandi_prices` for market-price requests. "
        "If the question is missing key detail, ask one short clarifying question. "
        "If you recommend purchasable inputs, follow the product recommendation fenced-block format exactly."
    ),
    tools=[
        core_skill_toolset,
        get_farmer_profile,
        search_knowledge_documents,
        search_mandi_prices,
    ],
)

diagnosis_agent = Agent(
    name="diagnosis_agent",
    model=config.default_model,
    description="Analyzes crop photos and helps farmers identify likely plant health issues, symptoms, and next steps.",
    instruction=(
        "You are the Plant Diagnosis Agent for Krishi AI. Use the attached image to inspect visible crop symptoms. "
        "Describe what you can see first, then explain the likely issue, practical next checks, and safe treatment advice. "
        "Use `search_knowledge_documents` when crop guidance should be grounded in the curated knowledge base. "
        "If the image is unclear, ask for one better follow-up photo or one missing detail."
    ),
    tools=[diagnosis_skill_toolset, get_farmer_profile, search_knowledge_documents],
)

lead_agent = Agent(
    name="lead_agent",
    model=config.default_model,
    description="Qualifies and creates supplier leads when the farmer clearly wants to buy an agricultural input or speak to a supplier.",
    instruction=(
        "You are the Lead Agent for Krishi AI. First ensure purchase intent is explicit. "
        "Use `get_farmer_profile` if profile completeness matters. "
        "Use `create_sales_lead` only when the farmer clearly wants supplier follow-up or to buy a specific product. "
        "If the tool returns needs_clarification, ask the next best short question instead of pretending the lead was created."
    ),
    tools=[core_skill_toolset, get_farmer_profile, create_sales_lead],
    before_tool_callback=validate_lead_tool_arguments,
)

clarification_agent = Agent(
    name="clarification_agent",
    model=config.default_model,
    description="Collects the next missing detail from the farmer using short, respectful follow-up questions.",
    instruction=(
        "You are the Clarification Agent for Krishi AI. Ask one short follow-up question at a time. "
        "Do not provide a long answer until the missing detail is clarified."
    ),
    tools=[core_skill_toolset],
)

root_agent = Agent(
    name="krishi_root_agent",
    model=config.default_model,
    description="Main coordinator for Krishi AI conversations.",
    instruction=(
        "You are Krishi AI, an agricultural assistant for Indian farmers. "
        "Delegate advice and market questions to `advice_agent`. "
        "Delegate image-based crop diagnosis or plant-health analysis to `diagnosis_agent`. "
        "Delegate missing-information conversations to `clarification_agent`. "
        "Delegate supplier-follow-up or buying requests to `lead_agent`. "
        "Always answer in the farmer's language. Keep answers practical, safe, and concise."
    ),
    tools=[core_skill_toolset],
    sub_agents=[advice_agent, diagnosis_agent, clarification_agent, lead_agent],
    before_model_callback=block_empty_turns,
)

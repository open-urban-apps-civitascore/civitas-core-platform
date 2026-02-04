Milestone retrospective. Run this after completing a milestone or when the user asks for a retro.

## Part 1: Data gathering (do silently before the conversation)

1. Read the full friction log (FR-xxx entries in BACKLOG.md)
2. Read recent CHANGELOG entries since last milestone
3. Read MILESTONES.md for current status
4. Read docs/claude/memory/ for patterns and gotchas added recently
5. Check what skills exist and how they were used

## Part 2: My feedback (present to user)

Based on the data, provide honest observations:

- **What went well**: Specific things that worked (patterns, skills, decisions)
- **What was wasteful**: Time sinks, repeated mistakes, unnecessary steps — cite FR-xxx entries
- **Process improvements I'd suggest**: Concrete proposals (new skills, config changes, workflow tweaks)
- **What the user could do differently**: Be honest and constructive. Examples:
  - "When you give me task X, including Y context upfront would save a round-trip"
  - "The review comments in area Z were the most impactful — more of those"
  - "I noticed you asked me to do X manually 3 times — want me to automate it?"
  - "Clarifying requirement X earlier would have avoided the rework in Y"

## Part 3: Interview the user

Ask these questions one at a time (wait for answers between each):

1. **What frustrated you most this milestone?** (process, not code)
2. **Where did I waste your time or make you repeat yourself?**
3. **Is there anything you keep having to remind me about?**
4. **What would make the biggest difference to your velocity next milestone?**

## Part 4: Action items

Based on both sets of feedback, propose concrete improvements:
- New FR-xxx entries for unresolved friction
- Updates to skills, CLAUDE.md, or memory files
- Changes to the task lifecycle protocol
- Anything that should change about how retros themselves work

Ask the user which ones to implement now vs. defer.

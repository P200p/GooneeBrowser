GooneeBrowser Non-Dev UX Enhancement
Transform GooneeBrowser from a developer-focused browser to a user-friendly browser that non-technical users can use confidently, while retaining all existing power features.

Phase 1: AI Tool Builder Enhancement 🧠
Create preset prompts data class and default presets
 Redesign AI Generator dialog to show preset buttons (เช่น "ซ่อนโฆษณา", "เปิด Dev Tools", "แปลหน้านี้")
 Add "ลองพูดดู" free-form input option
 Rename UI to "AI Tool Builder" (เครื่องมือ AI)
 Add multi-language support infrastructure (strings.xml)
Phase 2: Smart Shortcut System 🧩
 Rename "Shortcuts" → "เครื่องมือของฉัน" (My Tools)
 Add icon and description fields to Shortcut data class
 Create enhanced shortcut card layout with icons
 Add "ทดลองก่อนใช้" (Preview) button for AI-generated scripts
 Implement smart tool suggestion based on current page URL
Phase 3: Sandbox Mode for Beginners 🧼
 Add sandbox mode preference flag
 Implement script preview before injection
 Add "ดูว่าโค้ดทำอะไร" explanation dialog with human-readable descriptions
 Create safety confirmation dialog for script execution
Phase 4: Simple Theme & Onboarding 🎨
 Create "โหมดเรียบง่าย" (Simple Mode) that hides dev features
 Add simple mode toggle in settings
 Create onboarding dialog asking "คุณอยากให้เบราว์เซอร์ช่วยอะไรบ้าง?"
 Auto-add preset tools based on user's onboarding answers
 Add KEY_FIRST_RUN preference check
Phase 5: Behavior-Based Suggestions 🧭
 Track visited URLs with visit count
 Suggest setting frequently visited site as Home
 Show notification/Snackbar for suggestions

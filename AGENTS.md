# AGENTS.md

## 1. Purpose

This file defines how coding agents should work in this repository.

The product is **Erise / Erise-AI**, a private vertical project knowledge base system for individuals or lightweight teams. The core product capabilities are centered on:

- project management
- file upload, preview, download, and history tracking
- document reading and editing
- unified search and knowledge retrieval
- admin management
- future AI and RAG integration

The most important requirement for agents in this repository is:

> **Continuously upgrade the frontend into an enterprise-grade Vue 3 application with consistent design language, polished interactions, strong usability, and production-level maintainability.**

The frontend currently uses **Vue 3 + Vite + Vue Router + Axios + Element Plus**. Agents may introduce additional libraries only when they clearly improve product quality, developer efficiency, or UX consistency, and only if they do not fragment the visual system.

---

## 2. Product Positioning

Erise is not a demo admin panel. It is a **knowledge-work product**. All UI decisions must serve the following product goals:

1. make project and knowledge organization feel clear, calm, and efficient
2. make file/document/search operations obvious and low-friction
3. make frequently used pages feel professional and trustworthy
4. make long-session usage comfortable, readable, and stable
5. make enterprise-style behavior visible through consistency, not through visual clutter

Agents must prefer:

- structured information density
- strong readability
- consistent interaction patterns
- low cognitive load
- polished empty/loading/error states
- clear permissions and operation boundaries

Agents must avoid:

- flashy but inconsistent decoration
- mixed visual styles from multiple UI systems
- oversized spacing that wastes work area
- dense tables with poor hierarchy
- unbounded dialog nesting
- random colors, random radii, random shadows
- overusing gradients, glassmorphism, or animation

---

## 3. Expected Repository Shape

Based on the existing project context, the repository typically contains or aligns with modules similar to:

- `erise-ai-ui` → Vue 3 frontend
- `erise-backend-v7` → Spring Boot core backend
- `erise-backend-cloud-v7` → gateway / AI / cloud services
- separate admin-related pages or admin area inside frontend routing

Agents should assume the frontend is the main user-facing surface and optimize it first, while keeping API contracts aligned with backend and gateway conventions.

Do not redesign the frontend in isolation from backend realities.

---

## 4. Core Agent Priorities

When working in this repository, agents should prioritize work in the following order:

### P0: Usability and consistency

- unify layout, spacing, typography, and feedback behavior
- reduce visual noise
- improve discoverability of actions
- make the app usable for long daily sessions

### P1: Enterprise-grade frontend structure

- clean route architecture
- reusable layout shell
- reusable business components
- normalized API and error handling
- stable state management
- design tokens instead of scattered style values

### P2: Knowledge product experience

- document/file list efficiency
- search experience quality
- detail pages with strong information hierarchy
- preview/editor ergonomics
- permission-aware actions

### P3: Visual polish

- refined card, table, filter, and panel composition
- clean icon usage
- subtle motion
- better onboarding, empty states, and placeholders

### P4: Optional enhancements

- richer editor integrations
- better charts and usage dashboards
- drag-and-drop organization
- AI workspace UX

---

## 5. Tech Stack Rules

### Required baseline

- Vue 3
- Vite
- Vue Router
- Axios
- Element Plus
- `@element-plus/icons-vue`

### Strongly recommended additions

These are allowed and encouraged when needed:

- **Pinia** for medium-to-large shared state
- **VueUse** for composables and UI behavior utilities
- **unplugin-auto-import** and **unplugin-vue-components** for cleaner component imports
- **UnoCSS** or a small token-based utility layer for controlled styling acceleration
- **@iconify/vue** for a wider icon set when Element icons are insufficient
- **ECharts** for dashboards or analytics pages
- **md-editor-v3** or **wangeditor** for document editing if the current editor is weak
- **pdfjs-dist** for PDF preview
- **splitpanes** for document preview/editor dual-pane layouts
- **dayjs** for date formatting
- **nprogress** for global route loading indication

### Not recommended

Do not introduce a second full UI framework such as Ant Design Vue, Naive UI, or Vuetify into the same product surface. This causes visual fragmentation and maintainability issues.

Use Element Plus as the main component foundation. Other libraries should solve specific problems, not replace the design system.

---

## 6. Frontend Quality Standard

All frontend code must feel like it belongs to a serious enterprise product.

That means:

- consistent page shell
- consistent headers and toolbars
- consistent form rhythm
- consistent tables and operation columns
- consistent dialogs and drawers
- consistent empty/loading/error states
- consistent permissions and disabled states
- consistent confirmation flows
- consistent API messaging and toast behavior

A page is **not complete** if it only “works”. It is complete only when it also has:

- proper loading handling
- proper empty state
- proper error state
- proper responsive behavior
- readable spacing and typography
- safe destructive action confirmation
- accessible labels and keyboard-friendly controls where possible

---

## 7. Design System Requirements

Agents must establish or preserve a lightweight design system.

### 7.1 Design tokens

Centralize tokens. Do not hardcode random values repeatedly.

At minimum define tokens for:

- primary / success / warning / danger / info colors
- text colors: primary, regular, secondary, placeholder, disabled
- background colors: app, page, card, panel, hover
- border colors
- radius scale
- shadow scale
- spacing scale
- header heights
- sidebar widths
- transition durations

Prefer CSS variables in a dedicated file such as:

- `src/styles/tokens.css`
- `src/styles/variables.scss`
- `src/styles/theme/index.scss`

### 7.2 Visual tone

The product should feel:

- professional
- light but not childish
- modern but not trendy for trend’s sake
- restrained and trustworthy
- suitable for document-heavy workflows

### 7.3 Typography

Prefer a strong readability hierarchy:

- page title
- section title
- card title
- body text
- auxiliary text
- metadata text

Do not use many font sizes. A restrained scale is better.

### 7.4 Radius and shadows

Use soft radii and subtle shadows.

Recommended style direction:

- cards: medium radius
- inputs/buttons: small to medium radius
- shadows: subtle, low blur, low opacity
- borders: visible but understated

### 7.5 Color usage

Use color to indicate meaning, not decoration.

- primary color for key actions and focus
- success/warning/danger only for semantic state
- avoid large saturated color blocks unless they serve hierarchy
- do not use many unrelated accent colors on a single screen

---

## 8. Layout Rules

### 8.1 Global shell

The app should use a stable enterprise shell:

- left sidebar for navigation when appropriate
- top header for context, search entry, profile, and global actions
- breadcrumb or contextual title area
- content area with constrained padding rhythm

### 8.2 Page structure

A typical business page should follow:

1. page header
2. optional summary or metrics row
3. filter/search toolbar
4. main content block
5. pagination / infinite loading / secondary detail

### 8.3 Width and density

This product is document-heavy. Do not waste horizontal space.

Prefer:

- medium information density
- clear grouping
- compact but readable tables
- side panels/drawers for secondary actions

Avoid oversimplified marketing-style spacing inside management pages.

### 8.4 Responsive behavior

Support desktop first, then adapt gracefully to narrower screens.

At minimum:

- sidebar should collapse cleanly
- toolbars should wrap predictably
- tables should degrade gracefully
- drawers should replace some dialogs on small screens

---

## 9. Page-Level UX Rules

### 9.1 Login page

The login page must look credible and clean.

Requirements:

- centered login card or split-layout login hero
- clear visual hierarchy
- strong input spacing
- obvious captcha behavior if captcha is enabled
- visible loading state on submit
- clear error copy
- do not overdecorate

### 9.2 Dashboard / home page

The home page should provide clear entry points, not just placeholder cards.

Recommended modules:

- recent projects
- recent documents/files
- quick actions
- search entrance
- activity/history
- optional usage statistics

### 9.3 Project list / project detail

Projects are the product’s top-level business object.

Requirements:

- clear card/list view
- strong sorting and filtering
- project metadata readable at a glance
- detail page must separate overview, files, docs, members/tags, history

### 9.4 File and document pages

These are core product pages and deserve the highest polish.

Requirements:

- strong toolbar hierarchy
- predictable preview area
- readable metadata panel
- drag-and-drop upload where applicable
- visible version/history entry
- batch actions for list pages
- preview/editor modes should be easy to switch

### 9.5 Search page

Search is a core differentiator.

Requirements:

- clear input prominence
- useful filters
- grouped result types where applicable
- highlighted matched content
- obvious empty states
- fast perceived interaction

### 9.6 Admin pages

Admin pages should feel stricter and more compact than end-user pages.

Requirements:

- table-first layout
- strong auditability
- status tags and operation permissions
- destructive actions clearly separated

---

## 10. Component Strategy

Agents should build a reusable component system instead of repeating page-local code.

Recommended layering:

### Base UI wrappers

Reusable wrappers around Element Plus primitives, such as:

- `BasePageHeader`
- `BaseSection`
- `BaseCard`
- `BaseToolbar`
- `BaseEmpty`
- `BaseStatusTag`
- `BaseAsyncButton`
- `BaseTable`
- `BaseSearchForm`
- `BaseDrawerForm`

These should standardize spacing, slots, loading patterns, and tone.

### Business components

Reusable domain widgets such as:

- `ProjectCard`
- `ProjectSelector`
- `FilePreviewPanel`
- `DocumentMetaPanel`
- `SearchResultCard`
- `UploadDropzone`
- `HistoryTimeline`
- `PermissionGuard`
- `AiChatPanel`

### Rules

- do not duplicate the same toolbar, table, or detail header pattern across pages
- extract after the second strong repetition
- prefer composability over giant all-in-one components

---

## 11. Table, Form, Dialog, Drawer Standards

### 11.1 Tables

Tables must be readable, not cramped and not bloated.

Requirements:

- column width strategy must be intentional
- operation column fixed where useful
- use ellipsis with tooltip for long text
- status shown with tags/chips
- bulk selection only when meaningful
- pagination always visible when dataset is large
- empty state must be informative

If the table becomes too complex, agents may adopt a stronger table solution, but only if the interaction value is real.

### 11.2 Forms

Forms must be easy to scan.

Requirements:

- label alignment consistent
- validation messages clear
- required fields obvious
- related fields grouped
- long forms split into sections or steps
- submit area fixed or clearly visible when needed

### 11.3 Dialogs

Use dialogs for short focused tasks.

Do not put large forms, large tables, or heavy preview editors inside small dialogs.

### 11.4 Drawers

Use drawers for side editing, metadata inspection, and secondary workflows. For many enterprise interactions, a drawer is better than a modal.

---

## 12. Interaction Rules

### 12.1 Feedback

Every async action must provide feedback.

Use:

- inline loading for local actions
- skeletons for page blocks
- route progress for navigation-heavy flows
- success messages only when helpful
- errors that tell the user what happened and what to do next

### 12.2 Confirmation

Destructive actions require confirmation.

Examples:

- delete file
- delete project
- revoke permission
- clear history
- overwrite content

Prefer concise confirmations with explicit target names.

### 12.3 Empty states

Do not use generic “No Data” unless absolutely necessary.

Good empty states should explain:

- what this area is for
- why it is empty
- what action the user can take next

### 12.4 Permission states

If a user lacks permission:

- hide actions when appropriate
- disable with explanation when useful
- never let UI imply that forbidden actions are available

---

## 13. Vue Coding Conventions

### 13.1 Component style

Use the Composition API.

Prefer:

- `<script setup>`
- explicit props and emits
- composables for reusable logic
- smaller focused components

### 13.2 Language choice

If the current codebase is JavaScript-heavy, remain compatible.

For new medium-to-large modules, prefer **TypeScript** if it can be introduced incrementally without breaking the current toolchain.

Do not force a risky all-at-once migration.

### 13.3 State management

Rules:

- local page state stays local
- cross-page or user/session state goes to Pinia
- avoid prop drilling when state clearly belongs to a shared store or composable
- avoid giant global stores that contain everything

### 13.4 API layer

Centralize request handling.

Requirements:

- shared axios instance
- auth token injection
- refresh / expiry handling aligned with backend auth design
- unified error normalization
- request timeout strategy
- typed or documented API modules

Do not scatter raw axios calls throughout view files.

### 13.5 Routing

Routes should carry metadata where useful:

- title
- auth requirement
- permission code
- layout type
- keepAlive preference
- breadcrumb label

### 13.6 File naming

Prefer predictable naming:

- components: `PascalCase.vue`
- composables: `useXxx.ts` or `useXxx.js`
- stores: `xxx.store.ts`
- api modules: `xxx.api.ts`
- constants: `xxx.constants.ts`

---

## 14. Styling Rules

### 14.1 Styling approach

Agents may use scoped CSS, SCSS, or a controlled utility system, but must keep the project consistent.

Good rule:

- tokens global
- structure local
- one-off hacks discouraged

### 14.2 Avoid

- inline styles unless genuinely dynamic
- ad hoc magic numbers everywhere
- deeply nested selectors
- `!important` abuse
- mixing too many spacing conventions

### 14.3 Prefer

- utility helpers for common layout if a controlled utility layer exists
- CSS vars for theme values
- reusable class patterns for page shells, panels, and toolbars

---

## 15. Content and Copy Rules

This product is Chinese-first unless the page explicitly targets a bilingual surface.

UI copy should be:

- concise
- professional
- action-oriented
- not overly casual
- consistent across pages

Examples:

- use one consistent term for 项目, 文档, 文件, 标签, 历史记录, 管理后台
- do not mix “删除”, “移除”, “清空” unless their meanings are actually different
- keep success/error copy short and clear

---

## 16. Performance Rules

Agents must treat performance as part of UX.

Requirements:

- lazy load route-level pages
- lazy load heavy editors/previews when possible
- reduce oversized dependency additions
- debounce search inputs where appropriate
- avoid unnecessary re-renders in large list pages
- virtualize extremely long lists if needed

For file/document-heavy pages, perceived speed matters as much as raw speed.

---

## 17. Accessibility Rules

At minimum:

- buttons and inputs must have clear labels
- clickable text that behaves like a button should not be plain text
- color should not be the only status signal
- focus states should remain visible
- dialogs and drawers should behave predictably with keyboard and close actions

This is a business system, so practical accessibility improvements are preferred over theoretical perfection.

---

## 18. Backend and Integration Awareness

This repository includes or aligns with Spring Boot backend and gateway-based deployment. Frontend work must respect integration realities.

Agents should:

- align API paths with gateway conventions
- avoid hardcoded localhost ports in production code
- use environment variables for service base URLs
- handle captcha, auth, refresh token, and permission data according to backend contracts
- keep frontend fields aligned with backend DTOs and actual DB-backed constraints

If a UI feature requires backend support and that support is missing, agents should:

1. document the missing contract
2. add a graceful frontend fallback if possible
3. avoid fake-complete UI that cannot really work

---

## 19. Recommended Frontend Structure

A recommended frontend structure is:

```text
src/
  api/
  assets/
  components/
    base/
    business/
  composables/
  constants/
  layouts/
  router/
  stores/
  styles/
    tokens/
    mixins/
    pages/
  utils/
  views/
    auth/
    dashboard/
    projects/
    documents/
    files/
    search/
    admin/
    ai/
```

Agents do not need to force this exact structure if the repository already uses a different but coherent structure. The rule is to move toward clarity and reuse, not churn for its own sake.

---

## 20. Feature-Specific Guidance for Erise

### 20.1 Project management

- emphasize project identity and status
- show recent activity and quick actions
- make project entry and switching efficient

### 20.2 File management

- prioritize upload clarity, status visibility, and batch operations
- support preview-first workflows
- show file type, size, update time, source, and tags cleanly

### 20.3 Document editing and preview

- editing area must be distraction-controlled
- preview area must preserve readability
- code block, image, PDF, and rich text handling should feel integrated, not bolted on

### 20.4 Search

- unify keyword search, filters, and result categorization
- highlight relevance cleanly
- support quick jump into target content

### 20.5 AI / RAG surface

If the AI module is exposed in the UI:

- keep chat workspace focused and not gimmicky
- show source attribution clearly when available
- distinguish user input, assistant response, references, and status
- make conversation actions safe and predictable

---

## 21. Definition of Done for UI Work

A UI task is done only if all conditions below are met:

- implementation matches business intent
- visual style matches the repository design language
- layout spacing is clean and consistent
- loading / empty / error states are handled
- desktop usage is comfortable
- no obvious API coupling issues remain
- components are reusable when repetition exists
- code is maintainable and not excessively page-local

If a page looks “functional but rough”, it is not done.

---

## 22. What Agents Should Do Before Editing

Before making changes, agents should:

1. inspect related routes, views, API modules, and shared components
2. identify repeated UI patterns worth extracting
3. inspect existing tokens/theme usage before adding new styles
4. understand whether the page is end-user-facing or admin-facing
5. keep backend contract realities in mind

Do not start by patching random styles directly into one page without checking the surrounding structure.

---

## 23. What Agents Should Do When Adding New UI

When adding new UI, agents should:

1. reuse existing layout shell and card/panel patterns
2. reuse shared toolbar/form/table conventions
3. add tokens instead of hardcoding new visual values
4. prefer drawer or split-pane patterns for heavy workflows
5. write clean page states from the start
6. ensure Chinese copy is consistent

---

## 24. What Agents Must Not Do

Agents must not:

- introduce a second dominant design system
- add random third-party widgets with mismatched style
- create pages with no loading or empty state
- use hardcoded API URLs in page components
- duplicate major business components across pages
- overanimate enterprise workflows
- replace readable dense layouts with oversized decorative layouts
- hide important operations behind unclear icons only
- break backend-aligned auth, captcha, permission, or upload flows

---

## 25. Preferred UI Direction

The preferred UI direction for this project is:

- **Element Plus as the base**
- **a custom enterprise knowledge-product skin on top**
- **clean dashboard/admin hybrid style**
- **better hierarchy, better spacing, better component reuse**
- **quiet, premium, reliable visual language**

Think:

- more Notion/Linear/modern enterprise knowledge platform in clarity
- more enterprise admin maturity in behavior and structure
- less template-marketplace feeling
- less generic CRUD-only feeling

---

## 26. Suggested Incremental Refactor Order

When improving the frontend, agents should generally refactor in this order:

1. global theme tokens
2. app layout shell
3. page header / toolbar / card primitives
4. table and form patterns
5. login and dashboard polish
6. project list and detail pages
7. file/document pages
8. search experience
9. admin pages
10. AI workspace

This order usually gives the highest product-level improvement with the lowest risk.

---

## 27. Commands and Working Assumptions

Common frontend assumptions for this repository:

- install dependencies with `npm install`
- local dev with `npm run dev`
- production build with `npm run build`
- preview with `npm run preview`

If the repository uses pnpm or yarn instead, agents should follow the existing lockfile and package manager already present in the repo.

Do not switch package managers casually.

---

## 28. Final Rule

Every frontend change should answer this question:

> Does this make Erise feel more like a polished enterprise knowledge product, rather than a stitched-together admin demo?

If the answer is not clearly yes, revise the implementation.

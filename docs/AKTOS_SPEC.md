# Ақтөс / Aktos mascot specification

Ақтөс is Ortaq's friendly orange-and-white corgi, a roommate companion rather than a debt collector. The user's existing reference image is the visual source of truth. A local, **untracked** `docs/img/aktos-reference.jpg` appeared during planning and was visually inspected: it is an eight-pose sheet with Kazakh labels and the intended corgi. Its provenance and permission to add it to the public repository remain unconfirmed, so the documentation commit does not include it. The human must approve rights and tracking before asset production. Do not invent a replacement dog. If the reference contradicts a detail below, review with the human and update this spec before drawing. Sticker generation is independent of core bot release.

## Fixed identity and visual consistency

Keep the reference's honey-orange coat, a distinctive **white chest**, broad white forehead blaze and muzzle, large upright pink-inner ears, round dark eyes, dark maroon outline, short legs and small tail. The eight-pose sheet shows waving, happy, winking/thoughtful, sleeping, full, playful, mildly sad and surprised expressions; use these as continuity anchors for the remaining poses. Preserve the reference's specific orange shade, white blaze shape, ear color pattern, nose, tail visibility, outline thickness and proportions. Use one master model sheet: front, three-quarter and side views, color swatches and face/ear/chest details. Stickers should read at Telegram chat size; favor one clear gesture and expression, transparent background, tight but uncut silhouette, consistent line weight and lighting. Avoid costume changes that hide the chest or ears; simple props (teacup, bowl, shopping bag, game controller, moon) are allowed. Do not use a different breed, generic puppy image or inconsistent face between stickers.

## Pack inventory and event mapping

| No. | Sticker title | Pose / prop | Bot event, if enabled |
| --- | --- | --- | --- |
| 01 | Сәлем! | Wave, bright eyes | First greeting in a group or private start |
| 02 | Бақытты | Tail-wagging happy face | Occasional completed group activity |
| 03 | Ойлы | Head tilt | Optional help/choice prompt, never on every tap |
| 04 | Шаршадым | Gentle tired expression | Optional cooking rota humor, never guilt |
| 05 | Тоқпын | Satisfied after food | Optional food response after a ready event |
| 06 | Ойнайық! | Controller or playful pose | Play/FIFA invitation |
| 07 | Ренжідім | Mild pout | Rare opt-in reaction; never shame someone for debt |
| 08 | Ойбай! | Surprised ears/eyes | Rare surprising but harmless reaction |
| 09 | Ас дайын! | Bowl and inviting gesture | Confirmed food-ready event |
| 10 | Шайға кел! | Teacup | Tea invitation |
| 11 | Дүкенге барамын | Reusable shopping bag | Store invitation |
| 12 | Қайырлы түн | Sleepy beside moon | Optional night reminder |

Urgent attention uses a concise text/mention by default; if a sticker is explicitly enabled for urgent events, reuse `Ойбай!` at most once per event, never as the only alert. Expense/purchase confirmation uses short text by default; `Бақытты` may appear rarely and must not imply purchase verification or payment receipt. No sticker on every balance, debt, deadline, reminder or normal chat message. One sticker per event maximum, with a household cooldown and a text fallback. A missing sticker ID must never fail the underlying bot action.

## Kazakh personality and copy

Ақтөс speaks as a warm, concise companion: informal `сен`, gentle invitations, no pressure, judgment or surveillance. Sample lines for localization keys: `Сәлем! Не істейміз?`, `Шай дайын, келесің бе?`, `Ас дайын! Келетіндер белгі берсін.`, `Дүкенге барамын. Не керек?`, `Ойнайық! Кім қосылады?`, `Қайырлы түн! Ертең көрісеміз.` Messages may use 0–2 emoji and one short sentence. Financial copy must stay factual: `Сен енгізген сатып алу сақталды.`; never `Ақтөс сатып алғаныңды растады` or similar claims. Store all static copy and sticker captions in `messages_kk.properties`, not Java literals.

## Asset preparation checklist

1. Obtain the original reference and permission to adapt it for a public repo/sticker pack. Record source and license privately or in a nonsecret manifest. Create a master model sheet and obtain human visual approval.
2. Produce the 12 poses from the same master; use transparent source layers and a consistent canvas, palette, outline and chest/facial markings. Export a contact sheet for side-by-side review. Keep editable source files and a manifest mapping pose key to file and version.
3. **At implementation time**, verify the current official Telegram sticker documentation for static/animated/video formats, dimensions, file-size limits, color/transparency rules, emoji assignment, pack naming and upload method. These limits can change; do not hard-code unverified numbers from this planning document. Record the documentation URL and verification date in the manifest. Validate every export automatically against those rules and inspect it on light/dark chat backgrounds at actual displayed size.
4. Human creates/uploads the pack using Telegram's official sticker tooling/BotFather or an approved bot API method, chooses a pack name and emoji for each sticker, and records returned sticker `file_id`s in deployment configuration. Do not commit real bot token or private API response. Verify the pack in the **development** bot before production configuration.
5. Integration maps domain events to optional configured IDs. Use Telegram `sendSticker` through the existing client, after the text/domain action succeeds, with per-event deduplication and cooldown. If send fails, log only event type and safe error class, keep text usable and avoid retry storms. Recheck current Telegram API behavior before implementation.

## Acceptance and release boundary

Human approves all 12 stickers against the supplied image; automated file checks pass the then-current Telegram requirements; test pack works in the dev group; no core action depends on sticker availability. Until provenance and rights for the local reference are confirmed, V2-07-01 is blocked and mascot assets are not guessed or marked complete. The release guide allows a documented core-only launch with mascot integration deferred, with a specific owner and follow-up date.

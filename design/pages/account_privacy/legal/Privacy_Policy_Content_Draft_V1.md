# 隐私政策 · Content Draft V1

Status: **LEGAL_REVIEW_REQUIRED**
Design structure: **FROZEN**
Production legal copy: **NOT APPROVED**

This file defines the required content architecture and current product facts that legal review must cover. It is not represented as final legal advice or a production-approved privacy policy.

## 1. Document metadata

Required before production approval:
- policy version;
- effective date;
- operator/legal entity name;
- contact method;
- applicable jurisdiction language reviewed by counsel.

## 2. Product purpose

Explain that YuJian provides:
- photo-based fish recognition;
- fish-catch records;
- Fish Guide / fish knowledge;
- account/profile functions;
- optional AI model-improvement contribution;
- optional location use when explicitly requested.

## 3. Information categories

Legal review must accurately describe, where actually applicable:

### Account information
Examples:
- username/account identifier;
- nickname;
- avatar;
- authentication/security information handled by the service.

Never imply storage of plaintext passwords.

### User-provided catch content
Examples:
- photos the user chooses to submit;
- fish species correction/confirmation;
- catch length/weight/location/notes where provided;
- associated fish-record metadata.

### Recognition processing
Explain the role of image processing required to provide fish recognition.

The policy must distinguish:
- service processing needed to perform recognition;
- optional use for AI model improvement.

### AI model improvement
Current product rule:

**OFF unless explicit valid consent exists.**

When enabled, the current design scope is limited to:
- the fish body/crop selected from the image;
- the species confirmed by the user;
- necessary metadata required to associate eligible correction data with model-improvement processing.

Withdrawal must be described accurately.

Do not broaden the policy to unrestricted photo-library/account-data training use without an approved product/privacy revision.

### Location
Current product rule:
- no location request merely because the app or settings page opens;
- location is requested only after the user explicitly chooses a location action such as `使用当前位置`;
- current intended purpose is to add location to the active FishRecord.

No background-location / passive trip tracking claim is authorized by current design.

### Device/service information
Only include categories actually collected by production systems after technical/legal verification.

Do not invent SDK telemetry categories.

## 4. Purposes of processing

Must distinguish purposes such as:
- account authentication and security;
- providing fish recognition;
- saving/displaying FishRecord;
- user-requested location attachment;
- optional model improvement after consent;
- service reliability/security where actually implemented.

Do not collapse optional model training into “service necessity”.

## 5. Permissions

Explain permission purpose and optionality for:
- camera/photo access as actually implemented;
- location as actually implemented.

The policy must not imply that location is mandatory for recognition or FishRecord saving.

## 6. Sharing / processors / SDKs

Production legal copy must list or link to actual:
- cloud providers;
- analytics/crash providers;
- model/API providers;
- other processors/SDKs

only after technical inventory verification.

No vendor may be invented in this draft.

## 7. Storage / retention / security

Legal review must cover:
- storage location / transfer where applicable;
- retention principles;
- deletion/retention exceptions;
- reasonable security measures.

Do not promise a retention period the product/backend cannot enforce.

## 8. User choices and rights

Describe actual current mechanisms and legally required request channels.

Current in-product choices include:
- AI model-improvement consent view/change;
- location-permission choice through OS;
- profile/account settings that actually exist.

Export My Data and Delete Account are currently **DEFERRED** product flows. Legal copy must not falsely imply those full in-app workflows are already available.

If rights can be exercised through a contact/support process, legal/legal-ops must provide the real channel before approval.

## 9. Minors

Legal review must add the correct age/minor treatment for the product's launch jurisdictions.

Do not fabricate an age threshold in Design Authority.

## 10. Policy changes

Define:
- how material policy updates are communicated;
- version/effective-date handling.

## 11. Contact

Requires approved operator/contact information before production.

## 12. Design Manager rule

- Visual Shell = **FROZEN**
- Content Structure = **FROZEN**
- Final Legal Copy = **LEGAL_REVIEW_REQUIRED**

Do not remove `LEGAL_REVIEW_REQUIRED` until approved legal text is supplied.

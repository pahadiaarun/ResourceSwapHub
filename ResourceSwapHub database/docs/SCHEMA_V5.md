# Schema V5 - Extended Profiles and Institutions

## New Tables

### institutions
Stores Indian schools/colleges/universities from official sources (UDISE+, AISHE).
- Tracks official codes, verification status, source metadata

### institution_aliases
Alternative names for institutions for fuzzy matching

### user_education
Multiple education records per user

### user_employment
Multiple employment records per user

### skills, user_skills
Normalized skills taxonomy

### subjects, user_subjects
Subjects with relationship types (CAN_HELP, WANT_HELP, INTERESTED)

### user_interests
User interests

### institution_suggestions
Manual institution submissions pending review

## Extended Users Table
Added fields for bio, location, preferences, user_type, and role-specific details.
All new fields are nullable/optional.

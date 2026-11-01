export type Board = "CBSE" | "ICSE" | "IB" | "STATE_BOARD" | "OTHER";
export type Condition = "NEW" | "LIKE_NEW" | "GOOD" | "FAIR" | "POOR";
export type PriceType = "FREE" | "FIXED_PRICE" | "NEGOTIABLE";
export type ListingStatus =
  | "AVAILABLE"
  | "UNAVAILABLE"
  | "WITHDRAWN"
  | "EXCHANGED";

/**
 * Standardized location resolved from the directory. Nulls are omitted from the
 * API payload (`non_null` inclusion), so every field is optional here.
 */
export interface LocationInfo {
  countryId?: string | null;
  stateId?: string | null;
  cityId?: string | null;
  countryName?: string | null;
  stateName?: string | null;
  cityName?: string | null;
}

export interface DirectoryCountry {
  id: string;
  isoCode: string;
  name: string;
}

export interface DirectoryState {
  id: string;
  countryId: string;
  officialCode?: string | null;
  name: string;
  stateType?: string | null;
}

export interface DirectoryDistrict {
  id: string;
  stateId: string;
  officialCode?: string | null;
  name: string;
}

export interface DirectoryCity {
  id: string;
  name: string;
  stateId: string;
  stateName?: string | null;
  districtId?: string | null;
  districtName?: string | null;
  cityType?: string | null;
}

export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string | null;
  city?: string | null;
  locality?: string | null;
  location?: LocationInfo | null;
  profileComplete: boolean;
  joinedAt?: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

export interface Book {
  id: string;
  title: string;
  author: string;
  publisher?: string | null;
  isbn?: string | null;
  classLevel: number;
  board: Board;
  boardLabel: string;
  subject: string;
  edition?: string | null;
  language: string;
}

export interface Seller {
  id: string;
  name: string;
  avatarUrl?: string | null;
  city?: string | null;
  locality?: string | null;
}

export interface CollectionRef {
  id: string;
  title: string;
}

export interface Listing {
  id: string;
  book: Book;
  seller: Seller;
  collection?: CollectionRef | null;
  condition: Condition;
  conditionLabel: string;
  priceType: PriceType;
  priceTypeLabel: string;
  price?: number | null;
  city: string;
  locality?: string | null;
  location?: LocationInfo | null;
  notes?: string | null;
  status: ListingStatus;
  imageUrls: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CollectionSummary {
  id: string;
  title: string;
  description?: string | null;
  city: string;
  locality?: string | null;
  location?: LocationInfo | null;
  owner: Seller;
  itemCount: number;
  availableCount: number;
  coverUrl?: string | null;
  createdAt: string;
}

export interface CollectionDetail extends CollectionSummary {
  items: Listing[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface TaxonomyOption {
  value: string;
  label: string;
}

export interface Taxonomy {
  boards: TaxonomyOption[];
  conditions: TaxonomyOption[];
  priceTypes: TaxonomyOption[];
  classes: number[];
  subjects: string[];
  languages: string[];
  cities: string[];
}

export interface Message {
  id: number;
  conversationId: string;
  sender: Seller;
  body: string;
  createdAt: string;
}

export interface Conversation {
  id: string;
  listingId?: string | null;
  listingTitle: string;
  listingAuthor: string;
  listingCoverUrl?: string | null;
  listingPriceType?: PriceType | null;
  listingPrice?: number | null;
  listingStatus?: ListingStatus | null;
  otherParticipant?: Seller | null;
  lastMessage?: Message | null;
  unreadCount: number;
  createdAt: string;
  lastMessageAt: string;
}

export interface MessagePage {
  messages: Message[];
  hasMore: boolean;
  nextBefore?: number | null;
}

export interface Media {
  id: string;
  url: string;
  contentType: string;
}

export interface SearchFilters {
  q?: string;
  subject?: string;
  classLevel?: string;
  board?: string;
  priceType?: string;
  condition?: string;
  city?: string;
  stateId?: string;
  cityId?: string;
  districtId?: string;
  locality?: string;
  page?: number;
  size?: number;
}

export type UserType = 
  | "SCHOOL_STUDENT"
  | "COLLEGE_STUDENT"
  | "WORKING_PROFESSIONAL"
  | "TEACHER_EDUCATOR"
  | "OTHER";

export type ProfileVisibility = "PUBLIC" | "PRIVATE" | "CONNECTIONS_ONLY";

export interface ExtendedProfile {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string | null;
  bio?: string | null;
  city?: string | null;
  locality?: string | null;
  state?: string | null;
  country?: string | null;
  location?: LocationInfo | null;
  preferredLanguage?: string | null;
  profileVisibility?: ProfileVisibility | null;
  userType?: UserType | null;
  profileComplete: boolean;
  profileCompletedExtended: boolean;
  // School
  schoolName?: string | null;
  schoolLocationState?: string | null;
  schoolLocationCity?: string | null;
  schoolLocationDistrict?: string | null;
  schoolClass?: string | null;
  schoolStream?: string | null;
  schoolBoard?: string | null;
  schoolAcademicYear?: string | null;
  schoolExpectedGraduationYear?: number | null;
  // College
  collegeName?: string | null;
  collegeInstitutionType?: string | null;
  collegeCity?: string | null;
  collegeState?: string | null;
  collegeCourse?: string | null;
  collegeBranch?: string | null;
  collegeDepartment?: string | null;
  collegeCurrentYearSemester?: string | null;
  collegeAdmissionYear?: number | null;
  collegeExpectedGraduationYear?: number | null;
  // Professional
  professionalDesignation?: string | null;
  professionalCompany?: string | null;
  professionalIndustry?: string | null;
  professionalWorkLocation?: string | null;
  professionalYearsExperience?: number | null;
  professionalHighestQualification?: string | null;
  professionalCollegeAttended?: string | null;
  // Teacher
  teacherInstitutionName?: string | null;
  teacherInstitutionType?: string | null;
  teacherSubjects?: string | null;
  teacherClassesTaught?: string | null;
  teacherYearsExperience?: number | null;
  teacherAreasExpertise?: string | null;
  teacherCanHelpWith?: string | null;
  // Other
  otherOccupation?: string | null;
  otherLearningGoals?: string | null;
  otherCanContribute?: string | null;
}

export interface Institution {
  id: string;
  name: string;
  institutionType: string;
  officialCode?: string | null;
  officialCodeType?: string | null;
  state?: string | null;
  district?: string | null;
  city?: string | null;
  source: string;
  verificationStatus: string;
}

export interface InstitutionFilters {
  states: string[];
  types: string[];
}

/** Per-record visibility for normalized profile entries. */
export type RecordVisibility = "PRIVATE" | "PLATFORM_USERS" | "PUBLIC";

export type EducationLevel =
  | "SCHOOL"
  | "HIGHER_SECONDARY"
  | "DIPLOMA"
  | "UNDERGRADUATE"
  | "POSTGRADUATE"
  | "DOCTORATE"
  | "OTHER";

export type EducationStatus = "CURRENT" | "COMPLETED" | "PAUSED" | "DISCONTINUED";

export interface Education {
  id: string;
  institutionId?: string | null;
  institutionName?: string | null;
  institutionNameManual?: string | null;
  educationLevel?: string | null;
  degree?: string | null;
  course?: string | null;
  branchSpecialization?: string | null;
  department?: string | null;
  schoolBoard?: string | null;
  grade?: string | null;
  academicStream?: string | null;
  startYear?: number | null;
  endYear?: number | null;
  currentYear?: string | null;
  currentSemester?: string | null;
  expectedCompletionYear?: number | null;
  academicSession?: string | null;
  educationStatus?: string | null;
  current: boolean;
  primary: boolean;
  gradePercentage?: number | null;
  visibility?: RecordVisibility | null;
  description?: string | null;
  createdAt: string;
  updatedAt: string;
}

/** POST/PATCH payload for an education record (all fields optional). */
export type EducationInput = Partial<
  Omit<Education, "id" | "institutionName" | "current" | "primary" | "createdAt" | "updatedAt">
> & {
  institutionId?: string | null;
  current?: boolean | null;
  primary?: boolean | null;
};

export type EmploymentType =
  | "FULL_TIME"
  | "PART_TIME"
  | "INTERNSHIP"
  | "CONTRACT"
  | "FREELANCE"
  | "SELF_EMPLOYED"
  | "OTHER";

export interface Employment {
  id: string;
  companyName?: string | null;
  designation?: string | null;
  industry?: string | null;
  employmentType?: string | null;
  workCity?: string | null;
  workState?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  current: boolean;
  visibility?: RecordVisibility | null;
  description?: string | null;
  skillsUsed?: string | null;
  createdAt: string;
  updatedAt: string;
}

export type EmploymentInput = Partial<
  Omit<Employment, "id" | "current" | "createdAt" | "updatedAt">
> & { current?: boolean | null };

export type TopicType =
  | "SKILL"
  | "SUBJECT"
  | "TECHNOLOGY"
  | "INTEREST"
  | "EXPERTISE"
  | "LEARNING_GOAL"
  | "EXAM";

export type TopicRelationType =
  | "INTERESTED_IN"
  | "LEARNING"
  | "CAN_HELP_WITH"
  | "EXPERIENCED_IN";

export type Proficiency = "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";

export interface Topic {
  id: string;
  canonicalName: string;
  topicType: string;
}

export interface UserTopic {
  id: string;
  topicId: string;
  canonicalName: string;
  topicType: string;
  relationType: TopicRelationType;
  proficiency?: Proficiency | null;
  visibility?: RecordVisibility | null;
}

/** One item in the full-replacement PUT /profile/me/topics payload. */
export interface UserTopicInput {
  topicName?: string | null;
  topicId?: string | null;
  topicType?: string | null;
  relationType: TopicRelationType;
  proficiency?: Proficiency | null;
  visibility?: RecordVisibility | null;
}

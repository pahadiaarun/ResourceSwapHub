export type Board = "CBSE" | "ICSE" | "IB" | "STATE_BOARD" | "OTHER";
export type Condition = "NEW" | "LIKE_NEW" | "GOOD" | "FAIR" | "POOR";
export type PriceType = "FREE" | "FIXED_PRICE" | "NEGOTIABLE";
export type ListingStatus =
  | "AVAILABLE"
  | "UNAVAILABLE"
  | "WITHDRAWN"
  | "EXCHANGED";

export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string | null;
  city?: string | null;
  locality?: string | null;
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

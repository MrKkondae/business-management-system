export type AuthenticatedUser = {
  userId: string;
  loginId: string;
  displayName: string;
};

export type AuthenticatedRole = {
  roleId: string;
  roleName: string;
};

export type AuthorizedMenu = {
  menuId: string;
  parentMenuId: string | null;
  menuName: string;
  menuUrl: string;
  sortOrder: number;
};

export type CurrentUserResponse = {
  user: AuthenticatedUser;
  roles: AuthenticatedRole[];
  menus: AuthorizedMenu[];
  passwordChangeRequired: boolean;
  idleTimeoutSeconds: number;
  absoluteSessionExpiresAt: string;
};

export type CsrfTokenResponse = {
  headerName: string;
  token: string;
};

export type LoginRequest = {
  loginId: string;
  password: string;
};

export type InitialRegistrationRequest = {
  newPassword: string;
  newPasswordConfirmation: string;
  emailAddress: string | null;
  mobileNumber: string | null;
};

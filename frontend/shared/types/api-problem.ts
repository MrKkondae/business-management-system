export type ApiFieldErrorReason =
  | "REQUIRED"
  | "SIZE"
  | "FORMAT"
  | "INVALID";

export type ApiFieldError = {
  field: string;
  reason: ApiFieldErrorReason;
  message: string;
};

export type ApiProblem = {
  code: string;
  message: string;
  traceId: string;
  fieldErrors: ApiFieldError[];
};

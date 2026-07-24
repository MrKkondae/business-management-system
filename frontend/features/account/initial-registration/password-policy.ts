export type PasswordPolicyContext = {
  loginId: string;
  displayName: string;
};

export type PasswordPolicyCheck = {
  id: string;
  label: string;
  passed: boolean;
};

const weakFragments = [
  "password",
  "qwerty",
  "letmein",
  "welcome",
  "admin",
];

function containsPersonalValue(password: string, value: string): boolean {
  const candidate = value.toLowerCase();
  return candidate.length >= 3 && password.includes(candidate);
}

function hasFourCharacterSequence(password: string): boolean {
  for (let start = 0; start <= password.length - 4; start += 1) {
    const direction =
      password.charCodeAt(start + 1) - password.charCodeAt(start);
    if (Math.abs(direction) !== 1) {
      continue;
    }
    let sequence = true;
    for (let offset = 2; offset < 4; offset += 1) {
      if (
        password.charCodeAt(start + offset) -
          password.charCodeAt(start + offset - 1) !==
        direction
      ) {
        sequence = false;
        break;
      }
    }
    if (sequence) {
      return true;
    }
  }
  return false;
}

function hasFourRepeatedCharacters(password: string): boolean {
  return /(.)\1\1\1/u.test(password);
}

function isRepeatedPattern(password: string): boolean {
  for (
    let patternLength = 1;
    patternLength <= password.length / 2;
    patternLength += 1
  ) {
    if (password.length % patternLength !== 0) {
      continue;
    }
    const pattern = password.slice(0, patternLength);
    if (pattern.repeat(password.length / patternLength) === password) {
      return true;
    }
  }
  return false;
}

export function passwordPolicyChecks(
  password: string,
  context: PasswordPolicyContext,
): PasswordPolicyCheck[] {
  const categoryCount = [
    /[A-Z]/u.test(password),
    /[a-z]/u.test(password),
    /\d/u.test(password),
    /[^A-Za-z0-9]/u.test(password),
  ].filter(Boolean).length;
  const lowerPassword = password.toLowerCase();
  const containsWeakFragment = weakFragments.some((fragment) =>
    lowerPassword.includes(fragment),
  );
  const containsPersonal =
    containsPersonalValue(lowerPassword, context.loginId) ||
    containsPersonalValue(lowerPassword, context.displayName);

  return [
    {
      id: "length",
      label: "12자 이상 64자 이하",
      passed: password.length >= 12 && password.length <= 64,
    },
    {
      id: "categories",
      label: "대문자·소문자·숫자·특수문자 중 3종류 이상",
      passed: categoryCount >= 3,
    },
    {
      id: "personal",
      label: "로그인ID와 사용자명 미포함",
      passed: password.length > 0 && !containsPersonal,
    },
    {
      id: "weak",
      label: "취약 문자열 미포함",
      passed: password.length > 0 && !containsWeakFragment,
    },
    {
      id: "pattern",
      label: "4자 연속·동일문자 반복·단순 반복 패턴 제외",
      passed:
        password.length > 0 &&
        !hasFourCharacterSequence(password) &&
        !hasFourRepeatedCharacters(password) &&
        !isRepeatedPattern(password),
    },
  ];
}

export function isPasswordPolicySatisfied(
  password: string,
  context: PasswordPolicyContext,
): boolean {
  return passwordPolicyChecks(password, context).every((check) => check.passed);
}

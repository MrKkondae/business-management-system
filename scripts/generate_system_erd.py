"""기존 시스템 관리 ERD 생성 명령의 호환성 래퍼."""

from generate_erd import generate_area


if __name__ == "__main__":
    print(f"GENERATED area=system path={generate_area('system')}")

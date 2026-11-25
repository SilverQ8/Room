# Room Planner (완성본)

2D에서 AutoCAD처럼 벽을 그리고, 방을 색칠하고, 버튼 하나로 3D 입체로 세워 보는 프로그램입니다.
학습 단계별 정답 코드를 겸합니다.

## 실행

| 파일 | 하는 일 |
|---|---|
| `run.bat` | 컴파일 후 실행 |
| `compile.bat` | 컴파일만 (`bin\`에 출력) |
| `test.bat` | 모델 검사 + 화면 자동 검사 (캡처는 `smoke-out\`) |

JavaFX SDK는 옆 폴더 `..\Room-main\javafx-sdk-21.0.3\lib` 를 사용합니다. 위치가 다르면 환경변수 `JAVAFX_LIB`에 lib 폴더 경로를 지정하세요.

## 조작법

| 입력 | 동작 |
|---|---|
| 클릭 | 벽의 점 지정 (연속으로 이어짐) |
| 시작점으로 돌아와 클릭 | 닫힌 도형 → **방 자동 생성 + 색칠** |
| 숫자 + Enter | 마우스 방향으로 그 길이(mm)만큼 벽 생성 |
| 우클릭 / ESC | 그리기 종료 |
| F8 / F9 / F3 | 직교 / 그리드 스냅 / 객체(끝점) 스냅 켜고 끄기 |
| L / V / D / W / F | 벽 그리기 / 선택 / 문 / 창문 / 가구 배치 도구 |
| 선택 도구: 클릭, Delete, C, N | 선택, 삭제, 색상, 이름 (방·가구 공통) |
| **왼쪽 가구 카탈로그** 카드 클릭 | 화면 중앙의 빈 자리에 가구를 **바로 추가**하고 선택 (이어서 드래그·R로 조정) |
| 카탈로그 카드 Shift+클릭 | 마우스로 위치를 정해서 배치하는 모드 (선택된 카드가 파란 테두리로 강조됨) |
| 가구 배치 중: 클릭 / R / 우클릭·ESC | 놓기(계속 놓을 수 있음) / 90° 회전 / 끝내기 |
| 선택 도구로 가구 드래그 | 이동 (100mm 단위로 붙음, F9로 해제) |
| 선택한 가구에서 R / 가구 크기 버튼 | 90° 회전 / 가로·깊이·높이(mm) 입력 |
| 가구가 **빨갛게** 표시됨 | 다른 가구나 벽과 겹침 (3D에서는 빨간 반투명 상자) |
| 휠 / 휠 버튼 드래그 / Home | 확대·축소 / 이동 / 전체 보기 |
| Ctrl+Z | 실행 취소 |
| **🔍 가구 쇼핑 추천** 버튼 | 네이버 쇼핑 실시간 검색 및 근사치 가구 추천 (원클릭 도면 배치) |
| **3D 입체** 탭 | 왼쪽 드래그 = 회전, 오른쪽 드래그 = 이동, 휠 = 확대·축소 |

저장 파일 확장자는 `.rplan` (탭으로 구분된 텍스트라 메모장으로 열어 볼 수 있습니다).

## 단계별로 어느 파일을 보면 되나

| 단계 | 내용 | 파일 |
|---|---|---|
| 1 | 데이터 모델 | `model/Point2D`, `Wall`, `Room`, `Plan` |
| 2 | 클릭으로 선 그리기 | `ui/PlanEditor` — `installMouse`, `commitPoint`, `paintComponent` |
| 3 | 그리드·스냅·직교 | `ui/PlanEditor` — `resolvePoint`, `drawGrid`, `findEndpointNear` |
| 4 | 닫힌 도형과 색칠 | `ui/PlanEditor` — `commitPoint`(방 생성), `drawRooms` / `model/Room` |
| 5 | 줌·팬·선택·삭제·실행 취소 | `ui/PlanEditor` — `screenToWorld`, `pick`, `undo` |
| 6 | 2D → 3D 입체화 | `ui/Viewer3D` — `addWall`(압출), `addFloor` / `model/Triangulator` |
| 7 | 문·창문, 저장/불러오기 | `model/Opening`, `model/PlanIO` |
| 8-1 | 가구 데이터 모델 | `model/Furniture` (종류 6개, 크기·위치·회전, 바닥 면 `corners()`) |
| 8-2 | 2D 가구 배치·이동·회전 | `ui/PlanEditor` — `ensureGhost`, `placePoint`, `case FURNITURE`, 드래그 이동, `drawFurniture` |
| 8-2b | 가구 카탈로그 패널 | `ui/CatalogPanel`(카드 UI), `PlanEditor.addFurnitureAtCenter`(빈 자리 찾기), `MainFrame.onCatalogPick` |
| 8-3 | 3D 가구 모델 | `ui/FurnitureModels` (기존 `FurnitureItem`의 Box 조합을 mm로 옮김), `ui/Viewer3D.build` |
| 8-4 | 충돌 검사 | `model/Geometry`(분리축 정리 SAT), `model/Plan.collides` / `wallSolids` |
| 9 | 네이버 쇼핑 실시간 가구 추천 | `ui/RecommendationDialog`, `service/NaverShoppingService`, `ProductDimensionParser` |

## 핵심 개념 요약

- **모델은 mm, 화면은 px.** 변환은 `screenToWorld` / `sx` / `sy` 한 곳에서만 합니다.
- **점 보정 함수 하나(`resolvePoint`)** 를 미리보기와 클릭이 함께 씁니다. 그래서 보이는 위치와 찍히는 위치가 항상 같습니다.
- **3D는 같은 `Plan`을 다시 해석할 뿐입니다.** 벽 = 상자(Box), 문·창문 = 벽을 조각내어 비운 자리, 바닥 = 다각형을 삼각형으로 쪼갠 메쉬.
- **가구 방향 규칙:** 회전 0°일 때 앞면은 도면 위쪽. 90° = 오른쪽, 180° = 아래, 270° = 왼쪽. 2D에서는 굵은 변이 앞면입니다. 3D에서는 같은 각도의 Y축 회전이 되어 벽과 같은 규칙으로 대응됩니다.
- **충돌은 바닥 면(회전된 사각형)끼리의 겹침으로 판정합니다.** 맞닿기만 한 것(1mm 이하)은 충돌이 아니며, 문 자리는 벽으로 치지 않습니다.
- **좌표 대응:** 도면 x → 3D X, 도면 y → 3D **-Z**, 높이 → 3D **-Y** (JavaFX는 Y가 아래쪽이 +). y를 -Z로 두어야 2D 도면과 같은 방향으로 보입니다.

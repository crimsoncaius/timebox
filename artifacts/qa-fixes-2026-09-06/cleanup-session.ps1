$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8001'
$expected = @{
  8='QA fix1 check parent'; 10='QA fix1 recurring'; 12='QA coordinator finding1';
  14='QA fix3 blocked task'; 15='QA fix3 drag task'; 16='QA fix3 blocked peer';
  17='QA fix3 completed peer'; 18='QA coordinator finding3'; 19='QA fix4 native deletion'
}
$tasks = (Invoke-RestMethod "$base/tasks").items
$preserved = @{
  task = $tasks | Where-Object id -eq 1
  projects = Invoke-RestMethod "$base/projects"
  settings = Invoke-RestMethod "$base/settings"
  futureDay = Invoke-RestMethod "$base/days/2099-01-01/preview"
}
$preserved | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $PSScriptRoot 'cleanup-user-data-before.json')
foreach($id in $expected.Keys) {
  $task = $tasks | Where-Object id -eq $id
  if(-not $task -or $task.title -ne $expected[$id]) {throw "QA task identity mismatch: $id"}
  if($task.planned_dates.Count -gt 0) {throw "Unexpected planned work on QA task: $id"}
}
$series = Invoke-RestMethod "$base/recurring-templates/1"
if($series.title -ne 'QA 0906 web series' -or $series.status -ne 'ended') {throw 'Original QA series identity mismatch'}
$day = Invoke-RestMethod "$base/days/2026-09-06"
$block = $day.time_blocks | Where-Object id -eq 766
if(-not $block -or -not $block.name.StartsWith('QA fix2')) {throw 'QA block identity mismatch'}
foreach($id in $expected.Keys) {
  Invoke-RestMethod "$base/tasks/$id" -Method Delete | Out-Null
  Invoke-RestMethod "$base/tasks/$id/permanent" -Method Delete | Out-Null
  Write-Output "Removed QA task $id and its Subtasks"
}
Invoke-RestMethod "$base/days/2026-09-06/blocks/766" -Method Delete | Out-Null
Invoke-RestMethod "$base/recurring-templates/1" -Method Delete | Out-Null
Write-Output 'Removed QA block766 and original ended QA series1'
$type = (Invoke-RestMethod "$base/task-types") | Where-Object id -eq 6
if($type -and $type.name -eq 'unspecified' -and $type.usage_count -eq 0) {
  Invoke-RestMethod "$base/task-types/6" -Method Delete | Out-Null
  Write-Output 'Removed unused QA-created default Task Type6'
}
$after = @{
  task = (Invoke-RestMethod "$base/tasks").items | Where-Object id -eq 1
  projects = Invoke-RestMethod "$base/projects"
  settings = Invoke-RestMethod "$base/settings"
  futureDay = Invoke-RestMethod "$base/days/2099-01-01/preview"
}
$after | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $PSScriptRoot 'cleanup-user-data-after.json')
# Server time is dynamic; compare durable values only.
foreach($key in @('task','projects','settings')) {
  if(($preserved[$key] | ConvertTo-Json -Depth 30 -Compress) -ne ($after[$key] | ConvertTo-Json -Depth 30 -Compress)) {throw "User data changed during cleanup: $key"}
}
if(($preserved.futureDay.planned_blocks | ConvertTo-Json -Depth 20 -Compress) -ne ($after.futureDay.planned_blocks | ConvertTo-Json -Depth 20 -Compress)) {throw 'User planned work changed'}
if($null -ne (Invoke-RestMethod "$base/actual-blocks/active")) {throw 'Unexpected active Actual Block'}
Write-Output 'PASS user Task1, projects, settings and planned work unchanged; no active Actual Block'

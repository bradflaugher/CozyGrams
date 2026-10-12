# Finds the one release on a Play track with the given status and prints its
# version code.
#
#     bundle exec ruby .github/scripts/play_release.rb <package> <json key> <track> <status> [version code]
#
# play-promote.yml runs this before touching production, because fastlane
# supply doesn't check release status itself:
# - promote: the release on alpha must be completed (really in testing).
#   Given a version code, supply would promote a draft or halted release too.
# - update rollout: the release on production must be inProgress. supply's
#   update_rollout also picks a draft, and completing one publishes it.
# The version code printed is passed on to supply, so it acts on exactly the
# release checked here. The gems come with fastlane (Gemfile.lock).
require "googleauth"
require "google/apis/androidpublisher_v3"

package, key, track_name, status, version_code = ARGV
if [package, key, track_name, status].any? { |arg| arg.to_s.empty? }
  abort "usage: play_release.rb <package> <json key> <track> <status> [version code]"
end
unless version_code.to_s.empty? || version_code.match?(/\A[1-9][0-9]*\z/)
  abort "version code must be a positive whole number, not #{version_code}"
end

publisher = Google::Apis::AndroidpublisherV3
service = publisher::AndroidPublisherService.new
service.authorization = Google::Auth::ServiceAccountCredentials.make_creds(
  json_key_io: File.open(key),
  scope: publisher::AUTH_ANDROIDPUBLISHER
)

# Tracks are read through a throwaway edit, deleted again without committing.
edit = service.insert_edit(package)
begin
  track = service.get_edit_track(package, edit.id, track_name)
ensure
  service.delete_edit(package, edit.id)
end

all = track.releases || []
releases = all.select { |release| release.status == status }
unless version_code.to_s.empty?
  releases = releases.select { |release| (release.version_codes || []).map(&:to_i).include?(version_code.to_i) }
end

if releases.size != 1
  wanted = version_code.to_s.empty? ? "one #{status} release" : "a #{status} release of version code #{version_code}"
  found = all.map { |release| "#{release.status} #{(release.version_codes || []).join(',')}" }
  abort "#{track_name} has no #{wanted} (#{track_name} releases: #{found.empty? ? 'none' : found.join('; ')})"
end

puts releases.first.version_codes.map(&:to_i).max
